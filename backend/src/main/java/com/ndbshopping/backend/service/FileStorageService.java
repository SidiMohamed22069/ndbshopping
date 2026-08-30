package com.ndbshopping.backend.service;

import com.ndbshopping.backend.config.AppProperties;
import com.ndbshopping.backend.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final long MAX_VIDEO_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private static final Map<String, String> EXT_BY_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path root;

    public FileStorageService(AppProperties appProperties) {
        this.root = Path.of(appProperties.upload().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier d'upload: " + this.root, e);
        }
    }

    public String storeProductImage(Long productId, MultipartFile file) {
        return storeImage("products", productId, file);
    }

    /**
     * Stocke une vidéo produit. Le type réel est déterminé par les octets magiques
     * (ftyp → mp4, EBML → webm), pas par l'extension déclarée.
     */
    public String storeProductVideo(Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier vidéo manquant");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw ApiException.badRequest("Fichier trop volumineux (max 20 Mo)");
        }
        try {
            byte[] header = file.getInputStream().readNBytes(16);
            String ext = detectVideoExtension(header);
            if (ext == null) {
                throw ApiException.badRequest("Format de vidéo non autorisé (mp4, webm uniquement)");
            }
            String filename = UUID.randomUUID() + ext;
            Path dir = root.resolve("products").resolve(String.valueOf(productId)).resolve("videos");
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return "products/" + productId + "/videos/" + filename;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException e) {
            log.error("Échec de sauvegarde de la vidéo produit {}", productId, e);
            throw ApiException.serviceUnavailable("Impossible d'enregistrer la vidéo");
        }
    }

    /**
     * @return ".mp4", ".webm" ou null si le contenu n'est pas reconnu
     */
    static String detectVideoExtension(byte[] header) {
        if (header == null || header.length < 4) {
            return null;
        }
        if (header.length >= 8
                && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            return ".mp4";
        }
        if ((header[0] & 0xFF) == 0x1A
                && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF
                && (header[3] & 0xFF) == 0xA3) {
            return ".webm";
        }
        return null;
    }

    public String storeCategoryImage(Long categoryId, MultipartFile file) {
        return storeImage("categories", categoryId, file);
    }

    public String storePublicationImage(Long publicationId, MultipartFile file) {
        return storeImage("publications", publicationId, file);
    }

    public void deleteStoredImage(String imageUrlOrRelativePath) {
        deleteIfExists(toRelativePath(imageUrlOrRelativePath));
    }

    public void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            log.warn("Tentative de suppression hors dossier d'upload: {}", relativePath);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier {}", target, e);
        }
    }

    public Path resolve(String relativePath) {
        return root.resolve(relativePath).normalize();
    }

    private String storeImage(String folder, Long entityId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier image manquant");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Fichier trop volumineux (max 5 Mo)");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw ApiException.badRequest("Format d'image non autorisé (jpg, png, webp uniquement)");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extFromName = extensionOf(original);
        String ext = EXT_BY_TYPE.getOrDefault(contentType, extFromName.isBlank() ? ".jpg" : extFromName);
        String filename = UUID.randomUUID() + ext;
        Path dir = root.resolve(folder).resolve(String.valueOf(entityId));
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return folder + "/" + entityId + "/" + filename;
        } catch (IOException e) {
            log.error("Échec de sauvegarde de l'image {} {}", folder, entityId, e);
            throw ApiException.serviceUnavailable("Impossible d'enregistrer l'image");
        }
    }

    public static String toPublicUrl(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        String path = stored.replace("\\", "/");
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("/media")) {
            return path;
        }
        return path.startsWith("/") ? "/media" + path : "/media/" + path;
    }

    public static String toRelativePath(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        String path = stored.replace("\\", "/");
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return null;
        }
        if (path.startsWith("/media/")) {
            return path.substring("/media/".length());
        }
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    private static String extensionOf(String filename) {
        int i = filename.lastIndexOf('.');
        if (i < 0) {
            return "";
        }
        return filename.substring(i).toLowerCase(Locale.ROOT);
    }
}
