package com.ndbshopping.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ndbshopping.backend.dto.product.ProductImportPreviewResponse;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import de produit assisté par URL externe (Alibaba, AliExpress, Amazon, ...).
 *
 * Le serveur récupère lui-même la page (métadonnées Open Graph + JSON-LD schema.org)
 * et propose un aperçu (titre, description, images, prix d'origine, catégorie
 * suggérée) que l'admin relit et corrige avant publication : rien n'est enregistré
 * par cette étape, {@link ProductService#addImageFromUrl} ne télécharge les images
 * qu'une fois le produit créé et validé par l'admin.
 */
@Service
@Slf4j
public class ProductImportService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";
    private static final int MAX_HTML_BYTES = 3 * 1024 * 1024;
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGES_RETURNED = 6;
    private static final int MAX_REDIRECTS = 4;

    private static final Pattern PRICE_CHARS = Pattern.compile("[^0-9.,]");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    public ProductImportService(CategoryRepository categoryRepository, ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
    }

    public ProductImportPreviewResponse preview(String rawUrl) {
        URI uri = parseUri(rawUrl);
        String html = fetchHtml(uri);
        Document doc = Jsoup.parse(html, uri.toString());

        String title = firstNonBlank(metaContent(doc, "meta[property=og:title]", "meta[name=twitter:title]"), doc.title());
        String description = metaContent(doc, "meta[property=og:description]", "meta[name=description]", "meta[name=twitter:description]");
        List<String> images = new ArrayList<>(collectMetaImages(doc));
        PriceInfo priceInfo = extractMetaPrice(doc);

        JsonLdData jsonLd = extractJsonLd(doc);
        if (jsonLd != null) {
            if (isBlank(title)) {
                title = jsonLd.name();
            }
            if (isBlank(description)) {
                description = jsonLd.description();
            }
            if (images.isEmpty() && jsonLd.images() != null) {
                images.addAll(jsonLd.images());
            }
            if (priceInfo.price() == null && jsonLd.price() != null) {
                priceInfo = jsonLd.toPriceInfo();
            }
        }

        title = truncate(cleanText(title), 255);
        description = truncate(cleanText(description), 2000);
        List<String> dedupedImages = dedupe(images, MAX_IMAGES_RETURNED);
        Category suggested = suggestCategory(title, description);
        ProductSource source = ProductSource.fromUrl(uri.toString());

        return new ProductImportPreviewResponse(
                isBlank(title) ? null : title,
                isBlank(description) ? null : description,
                priceInfo.price(),
                isBlank(priceInfo.currency()) ? null : priceInfo.currency().trim().toUpperCase(Locale.ROOT),
                dedupedImages,
                suggested == null ? null : suggested.getId(),
                suggested == null ? null : suggested.getNom(),
                source,
                uri.toString()
        );
    }

    public record DownloadedImage(byte[] bytes, String contentType) {
    }

    public DownloadedImage downloadImage(String rawUrl) {
        URI uri = parseUri(rawUrl);
        HttpResponse<InputStream> response = sendWithRedirects(uri, MAX_REDIRECTS);
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ApiException.badRequest("Image source inaccessible (" + response.statusCode() + ")");
            }
            byte[] bytes = body.readNBytes(MAX_IMAGE_BYTES + 1);
            if (bytes.length == 0) {
                throw ApiException.badRequest("Image vide");
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw ApiException.badRequest("Image trop volumineuse (5 Mo maximum)");
            }
            String headerType = response.headers().firstValue("Content-Type").orElse("");
            return new DownloadedImage(bytes, resolveImageContentType(headerType, bytes));
        } catch (IOException e) {
            throw ApiException.serviceUnavailable("Impossible de télécharger l'image");
        }
    }

    // -------------------------------------------------------------------
    // Récupération réseau
    // -------------------------------------------------------------------

    private String fetchHtml(URI uri) {
        HttpResponse<InputStream> response = sendWithRedirects(uri, MAX_REDIRECTS);
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ApiException.badRequest("La page source a répondu avec une erreur (" + response.statusCode() + ")");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.isBlank() && !isHtmlLike(contentType)) {
                throw ApiException.badRequest("L'URL fournie ne pointe pas vers une page HTML");
            }
            byte[] bytes = body.readNBytes(MAX_HTML_BYTES);
            return new String(bytes, charsetFrom(contentType));
        } catch (IOException e) {
            throw ApiException.serviceUnavailable("Impossible de lire la page source");
        }
    }

    private HttpResponse<InputStream> sendWithRedirects(URI uri, int redirectsLeft) {
        UrlSafetyGuard.assertSafe(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,image/*,*/*;q=0.8")
                .header("Accept-Language", "fr,en;q=0.8")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw ApiException.serviceUnavailable("Impossible de contacter l'URL fournie");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.serviceUnavailable("Requête interrompue");
        }
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            String location = response.headers().firstValue("Location").orElse(null);
            closeQuietly(response);
            if (location == null || redirectsLeft <= 0) {
                throw ApiException.badRequest("Redirection invalide ou trop de redirections");
            }
            return sendWithRedirects(uri.resolve(location), redirectsLeft - 1);
        }
        return response;
    }

    private static void closeQuietly(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException ignored) {
            // rien à faire : le flux n'a jamais été lu
        }
    }

    private static URI parseUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw ApiException.badRequest("URL manquante");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (uri.getHost() == null) {
                throw ApiException.badRequest("URL invalide");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw ApiException.badRequest("URL invalide");
        }
    }

    private static boolean isHtmlLike(String contentType) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("text/html") || lower.contains("xhtml") || lower.contains("text/plain");
    }

    private static Charset charsetFrom(String contentType) {
        Matcher matcher = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (Exception ignored) {
                // encodage inconnu : on retombe sur UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String resolveImageContentType(String headerType, byte[] bytes) {
        String cleaned = headerType == null ? "" : headerType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        Set<String> allowed = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
        if (allowed.contains(cleaned)) {
            return cleaned;
        }
        String sniffed = sniffImageType(bytes);
        if (sniffed != null) {
            return sniffed;
        }
        throw ApiException.badRequest("Format d'image non supporté (jpg, png, webp uniquement)");
    }

    private static String sniffImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    // -------------------------------------------------------------------
    // Extraction des métadonnées (Open Graph / JSON-LD)
    // -------------------------------------------------------------------

    private static String metaContent(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String value = el.hasAttr("content") ? el.attr("content") : el.text();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static List<String> collectMetaImages(Document doc) {
        List<String> images = new ArrayList<>();
        for (Element el : doc.select(
                "meta[property=og:image], meta[property=og:image:secure_url], "
                        + "meta[property=og:image:url], meta[name=twitter:image]")) {
            String abs = el.attr("abs:content");
            if (!abs.isBlank()) {
                images.add(abs);
            }
        }
        return images;
    }

    private record PriceInfo(BigDecimal price, String currency) {
    }

    private static PriceInfo extractMetaPrice(Document doc) {
        String priceMeta = metaContent(doc,
                "meta[property=og:price:amount]",
                "meta[property=product:price:amount]",
                "meta[itemprop=price]");
        String currencyMeta = metaContent(doc,
                "meta[property=og:price:currency]",
                "meta[property=product:price:currency]",
                "meta[itemprop=priceCurrency]");
        return new PriceInfo(parsePrice(priceMeta), currencyMeta);
    }

    private record JsonLdData(String name, String description, List<String> images, BigDecimal price, String currency) {
        PriceInfo toPriceInfo() {
            return new PriceInfo(price, currency);
        }
    }

    private JsonLdData extractJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.data());
                JsonNode product = findProductNode(root);
                if (product == null) {
                    continue;
                }
                String name = textOrNull(product.get("name"));
                String description = textOrNull(product.get("description"));
                List<String> images = extractImageField(product.get("image"));
                BigDecimal price = null;
                String currency = null;
                JsonNode offers = product.get("offers");
                JsonNode offer = offers == null ? null : (offers.isArray()
                        ? (offers.isEmpty() ? null : offers.get(0))
                        : offers);
                if (offer != null) {
                    price = parsePrice(textOrNull(offer.get("price")));
                    currency = textOrNull(offer.get("priceCurrency"));
                }
                if (name != null || description != null || !images.isEmpty() || price != null) {
                    return new JsonLdData(name, description, images, price, currency);
                }
            } catch (Exception e) {
                log.debug("JSON-LD illisible, ignoré : {}", e.getMessage());
            }
        }
        return null;
    }

    private JsonNode findProductNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findProductNode(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        JsonNode type = node.get("@type");
        if (type != null) {
            if (type.isTextual() && type.asText().equalsIgnoreCase("Product")) {
                return node;
            }
            if (type.isArray()) {
                for (JsonNode t : type) {
                    if (t.isTextual() && t.asText().equalsIgnoreCase("Product")) {
                        return node;
                    }
                }
            }
        }
        JsonNode graph = node.get("@graph");
        if (graph != null) {
            return findProductNode(graph);
        }
        return null;
    }

    private static List<String> extractImageField(JsonNode imageNode) {
        List<String> out = new ArrayList<>();
        if (imageNode == null) {
            return out;
        }
        if (imageNode.isTextual()) {
            out.add(imageNode.asText());
        } else if (imageNode.isArray()) {
            for (JsonNode n : imageNode) {
                if (n.isTextual()) {
                    out.add(n.asText());
                } else if (n.isObject() && n.has("url")) {
                    out.add(n.get("url").asText());
                }
            }
        } else if (imageNode.isObject() && imageNode.has("url")) {
            out.add(imageNode.get("url").asText());
        }
        return out;
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asText();
    }

    static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = PRICE_CHARS.matcher(raw).replaceAll("").trim();
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
                    ? cleaned.replace(".", "").replace(",", ".")
                    : cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            int idx = cleaned.lastIndexOf(',');
            cleaned = (cleaned.length() - idx - 1 <= 2) ? cleaned.replace(",", ".") : cleaned.replace(",", "");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------
    // Suggestion de catégorie
    // -------------------------------------------------------------------

    private Category suggestCategory(String title, String description) {
        String haystack = normalize(nullToEmpty(title) + " " + nullToEmpty(description));
        if (haystack.isBlank()) {
            return null;
        }
        Category best = null;
        int bestScore = 0;
        for (Category category : categoryRepository.findAll()) {
            String name = normalize(category.getNom());
            if (name.isBlank()) {
                continue;
            }
            int score = 0;
            for (String word : name.split("\\s+")) {
                if (word.length() >= 3 && haystack.contains(word)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = category;
            }
        }
        return best;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static String cleanText(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max).trim();
    }

    private static List<String> dedupe(List<String> values, int max) {
        Set<String> seen = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                seen.add(v.trim());
            }
            if (seen.size() >= max) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }
}
