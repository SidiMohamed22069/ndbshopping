package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.product.CsvImportResponse;
import com.ndbshopping.backend.dto.product.ImportUrlRequest;
import com.ndbshopping.backend.dto.product.ProductAttributeInput;
import com.ndbshopping.backend.dto.product.ProductImageResponse;
import com.ndbshopping.backend.dto.product.ProductRequest;
import com.ndbshopping.backend.dto.product.ProductResponse;
import com.ndbshopping.backend.dto.product.ProductVideoResponse;
import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.CategoryAttributeDefinition;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.ProductAttributeValue;
import com.ndbshopping.backend.entity.ProductImage;
import com.ndbshopping.backend.entity.ProductVideo;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.NotificationType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.CartItemRepository;
import com.ndbshopping.backend.repository.CategoryAttributeDefinitionRepository;
import com.ndbshopping.backend.repository.OrderItemRepository;
import com.ndbshopping.backend.repository.ProductImageRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.ProductVideoRepository;
import com.ndbshopping.backend.repository.ProductSpecifications;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ProductService {

    public static final int MAX_IMAGES_PAR_PRODUIT = 6;
    public static final int MAX_VIDEOS_PAR_PRODUIT = 2;

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final CategoryAttributeDefinitionRepository attributeDefinitionRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVideoRepository productVideoRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public ProductService(
            ProductRepository productRepository,
            CategoryService categoryService,
            CategoryAttributeDefinitionRepository attributeDefinitionRepository,
            ProductImageRepository productImageRepository,
            ProductVideoRepository productVideoRepository,
            OrderItemRepository orderItemRepository,
            CartItemRepository cartItemRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService
    ) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.attributeDefinitionRepository = attributeDefinitionRepository;
        this.productImageRepository = productImageRepository;
        this.productVideoRepository = productVideoRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchPublic(
            Long categoryId,
            BigDecimal minPrix,
            BigDecimal maxPrix,
            String q,
            Pageable pageable
    ) {
        return search(ProductStatus.PUBLIE, categoryId, minPrix, maxPrix, q, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchAdmin(
            ProductStatus statut,
            Long categoryId,
            BigDecimal minPrix,
            BigDecimal maxPrix,
            String q,
            Pageable pageable
    ) {
        return search(statut, categoryId, minPrix, maxPrix, q, pageable);
    }

    private PageResponse<ProductResponse> search(
            ProductStatus statut,
            Long categoryId,
            BigDecimal minPrix,
            BigDecimal maxPrix,
            String q,
            Pageable pageable
    ) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );
        Page<Product> page = productRepository.findAll(
                ProductSpecifications.matching(statut, categoryId, minPrix, maxPrix, query),
                sorted
        );
        page.forEach(this::touchAssociations);
        return PageResponse.from(page.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getPublic(Long id) {
        return getVisible(id, null);
    }

    @Transactional(readOnly = true)
    public ProductResponse getVisible(Long id, User viewer) {
        Product product = get(id);
        if (product.getStatut() != ProductStatus.PUBLIE && !canManageListing(viewer, product)) {
            throw ApiException.notFound("Produit introuvable");
        }
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getAdmin(Long id) {
        Product product = get(id);
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = categoryService.get(request.categoryId());
        Product product = Product.builder()
                .nom(request.nom().trim())
                .description(request.description())
                .prix(request.prix())
                .stock(request.stock())
                .category(category)
                .sourceOrigine(request.sourceOrigine() == null ? ProductSource.MANUEL : request.sourceOrigine())
                .sourceUrl(request.sourceUrl())
                .statut(request.statut() == null ? ProductStatus.BROUILLON : request.statut())
                .build();
        applyAttributes(product, category.getId(), request.attributs());
        Product saved = productRepository.save(product);
        touchAssociations(saved);
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse submit(User user, ProductRequest request) {
        Category category = categoryService.get(request.categoryId());
        Product product = Product.builder()
                .nom(request.nom().trim())
                .description(request.description())
                .prix(request.prix())
                .stock(request.stock())
                .category(category)
                .sourceOrigine(request.sourceOrigine() == null ? ProductSource.MANUEL : request.sourceOrigine())
                .sourceUrl(request.sourceUrl())
                .statut(ProductStatus.EN_ATTENTE)
                .soumisPar(user)
                .build();
        applyAttributes(product, category.getId(), request.attributs());
        Product saved = productRepository.save(product);
        notificationService.createAndPush(
                NotificationType.PRODUIT_A_VALIDER,
                "Produit à valider : " + saved.getNom() + " — " + user.getNom(),
                "/products/" + saved.getId()
        );
        touchAssociations(saved);
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listMine(User user, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );
        Page<Product> page = productRepository.findBySoumisPar_Id(user.getId(), sorted);
        page.forEach(this::touchAssociations);
        return PageResponse.from(page.map(ProductResponse::from));
    }

    @Transactional
    public ProductResponse validate(Long id) {
        Product product = get(id);
        if (product.getStatut() != ProductStatus.EN_ATTENTE) {
            throw ApiException.badRequest("Le produit n'est pas en attente de validation");
        }
        product.setStatut(ProductStatus.PUBLIE);
        product.setRaisonRejet(null);
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse reject(Long id, String raison) {
        Product product = get(id);
        if (product.getStatut() != ProductStatus.EN_ATTENTE) {
            throw ApiException.badRequest("Le produit n'est pas en attente de validation");
        }
        product.setStatut(ProductStatus.REJETE);
        product.setRaisonRejet(raison.trim());
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse markSold(Long id, User user) {
        Product product = get(id);
        assertCanManageListing(user, product);
        if (product.getStatut() != ProductStatus.PUBLIE) {
            throw ApiException.badRequest(
                    "Le produit doit d'abord être validé et publié avant d'être marqué comme vendu");
        }
        product.setStatut(ProductStatus.VENDU);
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    /**
     * Archive uniquement depuis PUBLIE. Un produit VENDU reste VENDU (historique de vente),
     * il n'est pas basculé en ARCHIVE.
     */
    @Transactional
    public ProductResponse archive(Long id, User user) {
        Product product = get(id);
        assertCanManageListing(user, product);
        if (product.getStatut() != ProductStatus.PUBLIE) {
            throw ApiException.badRequest(
                    "Le produit doit d'abord être validé et publié avant d'être archivé");
        }
        product.setStatut(ProductStatus.ARCHIVE);
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse reactivate(Long id, User user) {
        Product product = get(id);
        assertCanManageListing(user, product);
        if (product.getStatut() != ProductStatus.VENDU && product.getStatut() != ProductStatus.ARCHIVE) {
            throw ApiException.badRequest("Seule une annonce vendue ou archivée peut être réactivée");
        }
        product.setStatut(ProductStatus.PUBLIE);
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = get(id);
        Category category = categoryService.get(request.categoryId());
        product.setNom(request.nom().trim());
        product.setDescription(request.description());
        product.setPrix(request.prix());
        product.setStock(request.stock());
        product.setCategory(category);
        if (request.sourceOrigine() != null) {
            product.setSourceOrigine(request.sourceOrigine());
        }
        product.setSourceUrl(request.sourceUrl());
        if (request.statut() != null) {
            product.setStatut(request.statut());
        }
        product.getAttributes().clear();
        applyAttributes(product, category.getId(), request.attributs());
        touchAssociations(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = get(id);
        if (orderItemRepository.existsByProductId(id)) {
            throw ApiException.conflict("Impossible de supprimer un produit déjà commandé");
        }
        cartItemRepository.deleteByProductId(id);
        product.getImages().forEach(img -> fileStorageService.deleteIfExists(img.getRelativePath()));
        product.getVideos().forEach(vid -> fileStorageService.deleteIfExists(vid.getRelativePath()));
        productRepository.delete(product);
    }

    @Transactional
    public ProductImageResponse addImage(Long productId, MultipartFile file) {
        return storeImage(get(productId), file);
    }

    @Transactional
    public ProductImageResponse addImage(Long productId, MultipartFile file, User user) {
        Product product = get(productId);
        assertCanEditImages(user, product);
        return storeImage(product, file);
    }

    private ProductImageResponse storeImage(Product product, MultipartFile file) {
        if (product.getImages().size() >= MAX_IMAGES_PAR_PRODUIT) {
            throw ApiException.conflict(
                    "Limite de 6 images atteinte. Supprimez-en une pour en ajouter une autre.");
        }
        int nextOrdre = product.getImages().stream()
                .mapToInt(ProductImage::getOrdre)
                .max()
                .orElse(-1) + 1;
        String relativePath = fileStorageService.storeProductImage(product.getId(), file);
        ProductImage image = productImageRepository.save(ProductImage.builder()
                .product(product)
                .relativePath(relativePath)
                .ordre(nextOrdre)
                .build());
        product.getImages().add(image);
        return new ProductImageResponse(image.getId(), "/media/" + relativePath, image.getOrdre());
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        removeImage(get(productId), imageId);
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId, User user) {
        Product product = get(productId);
        assertCanEditImages(user, product);
        removeImage(product, imageId);
    }

    private void removeImage(Product product, Long imageId) {
        ProductImage image = product.getImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Image introuvable"));
        fileStorageService.deleteIfExists(image.getRelativePath());
        product.getImages().remove(image);
        productImageRepository.delete(image);
    }

    private void assertCanEditImages(User user, Product product) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (product.getSoumisPar() == null || !product.getSoumisPar().getId().equals(user.getId())) {
            throw ApiException.forbidden("Vous ne pouvez modifier les images que de vos propres produits");
        }
        if (product.getStatut() != ProductStatus.EN_ATTENTE) {
            throw ApiException.forbidden("Impossible de modifier les médias après validation ou rejet");
        }
    }

    @Transactional
    public ProductVideoResponse addVideo(Long productId, MultipartFile file, User user) {
        Product product = get(productId);
        assertCanEditImages(user, product);
        if (product.getVideos().size() >= MAX_VIDEOS_PAR_PRODUIT) {
            throw ApiException.conflict(
                    "Limite de 2 vidéos atteinte. Supprimez-en une pour en ajouter une autre.");
        }
        int nextOrdre = product.getVideos().stream()
                .mapToInt(ProductVideo::getOrdre)
                .max()
                .orElse(-1) + 1;
        String relativePath = fileStorageService.storeProductVideo(product.getId(), file);
        ProductVideo video = productVideoRepository.save(ProductVideo.builder()
                .product(product)
                .relativePath(relativePath)
                .ordre(nextOrdre)
                .build());
        product.getVideos().add(video);
        return new ProductVideoResponse(
                video.getId(),
                "/media/" + relativePath,
                relativePath,
                video.getOrdre());
    }

    @Transactional
    public void deleteVideo(Long productId, Long videoId, User user) {
        Product product = get(productId);
        assertCanEditImages(user, product);
        ProductVideo video = product.getVideos().stream()
                .filter(vid -> vid.getId().equals(videoId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Vidéo introuvable"));
        fileStorageService.deleteIfExists(video.getRelativePath());
        product.getVideos().remove(video);
        productVideoRepository.delete(video);
    }

    /**
     * Import assisté par URL — stub MVP.
     * Pas de scraping automatique : le scraping de pages Facebook tierces viole les CGU Meta.
     * Une intégration Graph API n'est possible que pour une page que l'on administre.
     * On crée un brouillon pré-rempli (sourceUrl + sourceOrigine) à compléter par l'admin.
     */
    @Transactional
    public ProductResponse importFromUrl(ImportUrlRequest request) {
        Category category = categoryService.get(request.categoryId());
        String url = request.url().trim();
        Product product = Product.builder()
                .nom("Produit importé — à compléter")
                .description("Import depuis " + url + ". Renseignez le titre, le prix et les images avant publication.")
                .prix(BigDecimal.ZERO)
                .stock(null)
                .category(category)
                .sourceOrigine(detectSource(url))
                .sourceUrl(url)
                .statut(ProductStatus.BROUILLON)
                .build();
        Product saved = productRepository.save(product);
        touchAssociations(saved);
        return ProductResponse.from(saved);
    }

    @Transactional
    public CsvImportResponse importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier CSV manquant");
        }
        List<CsvImportResponse.CsvImportError> errors = new ArrayList<>();
        int imported = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                     CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .setIgnoreEmptyLines(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                int line = (int) record.getRecordNumber() + 1;
                try {
                    String nom = required(record, "nom");
                    String description = optional(record, "description");
                    BigDecimal prix = new BigDecimal(required(record, "prix"));
                    Integer stock = optional(record, "stock") == null || optional(record, "stock").isBlank()
                            ? null : Integer.parseInt(optional(record, "stock"));
                    Long categoryId = Long.parseLong(required(record, "categoryId"));
                    Category category = categoryService.get(categoryId);
                    productRepository.save(Product.builder()
                            .nom(nom)
                            .description(description)
                            .prix(prix)
                            .stock(stock)
                            .category(category)
                            .sourceOrigine(ProductSource.AUTRE)
                            .statut(ProductStatus.BROUILLON)
                            .build());
                    imported++;
                } catch (Exception ex) {
                    errors.add(new CsvImportResponse.CsvImportError(line, ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            throw ApiException.badRequest("CSV illisible : " + ex.getMessage());
        }
        return new CsvImportResponse(imported, errors);
    }

    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Produit introuvable"));
    }

    private void assertCanManageListing(User user, Product product) {
        if (!canManageListing(user, product)) {
            throw ApiException.forbidden("Vous ne pouvez modifier que vos propres annonces");
        }
    }

    private static boolean canManageListing(User user, Product product) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        return product.getSoumisPar() != null && product.getSoumisPar().getId().equals(user.getId());
    }

    private void applyAttributes(Product product, Long categoryId, List<ProductAttributeInput> attributs) {
        if (attributs == null || attributs.isEmpty()) {
            return;
        }
        for (ProductAttributeInput input : attributs) {
            CategoryAttributeDefinition def = attributeDefinitionRepository.findById(input.attributeDefinitionId())
                    .orElseThrow(() -> ApiException.badRequest("Attribut inconnu: " + input.attributeDefinitionId()));
            if (!def.getCategory().getId().equals(categoryId)) {
                throw ApiException.badRequest("L'attribut '" + def.getNomAttribut() + "' n'appartient pas à cette catégorie");
            }
            product.getAttributes().add(ProductAttributeValue.builder()
                    .product(product)
                    .attributeDefinition(def)
                    .valeur(input.valeur())
                    .build());
        }
    }

    private void touchAssociations(Product product) {
        product.getCategory().getNom();
        product.getImages().size();
        product.getVideos().size();
        product.getAttributes().forEach(a -> a.getAttributeDefinition().getNomAttribut());
        if (product.getSoumisPar() != null) {
            product.getSoumisPar().getId();
        }
    }

    static ProductSource detectSource(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("facebook.com") || lower.contains("fb.com") || lower.contains("fb.watch")) {
            return ProductSource.FACEBOOK;
        }
        if (lower.contains("alibaba.com") || lower.contains("aliexpress.com")) {
            return ProductSource.ALIBABA;
        }
        return ProductSource.AUTRE;
    }

    private static String required(CSVRecord record, String header) {
        if (!record.isMapped(header) || record.get(header) == null || record.get(header).isBlank()) {
            throw new IllegalArgumentException("Colonne '" + header + "' manquante");
        }
        return record.get(header);
    }

    private static String optional(CSVRecord record, String header) {
        if (!record.isMapped(header)) {
            return null;
        }
        return record.get(header);
    }
}
