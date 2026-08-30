package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.publication.PublicationRequest;
import com.ndbshopping.backend.dto.publication.PublicationResponse;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.Publication;
import com.ndbshopping.backend.entity.enums.PublicationStatus;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.PublicationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final ProductService productService;
    private final FileStorageService fileStorageService;

    public PublicationService(
            PublicationRepository publicationRepository,
            ProductService productService,
            FileStorageService fileStorageService
    ) {
        this.publicationRepository = publicationRepository;
        this.productService = productService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> listPublic(Pageable pageable) {
        Page<Publication> page = publicationRepository.findByStatutOrderByDatePublicationDesc(
                PublicationStatus.PUBLIE, pageable);
        return PageResponse.from(page.map(PublicationResponse::from));
    }

    @Transactional(readOnly = true)
    public List<PublicationResponse> listMisesEnAvant() {
        return publicationRepository
                .findTop8ByMisEnAvantTrueAndStatutOrderByDatePublicationDesc(PublicationStatus.PUBLIE)
                .stream()
                .map(PublicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> listAdmin(Pageable pageable) {
        Page<Publication> page = publicationRepository.findAllByOrderByDatePublicationDesc(pageable);
        return PageResponse.from(page.map(PublicationResponse::from));
    }

    @Transactional
    public PublicationResponse create(PublicationRequest request) {
        Publication publication = Publication.builder()
                .titre(request.titre().trim())
                .contenu(request.contenu())
                .imageUrl(request.imageUrl())
                .produitLie(resolveProduct(request.produitLieId()))
                .statut(request.statut() == null ? PublicationStatus.BROUILLON : request.statut())
                .misEnAvant(Boolean.TRUE.equals(request.misEnAvant()))
                .build();
        return PublicationResponse.from(publicationRepository.save(publication));
    }

    @Transactional
    public PublicationResponse update(Long id, PublicationRequest request) {
        Publication publication = get(id);
        publication.setTitre(request.titre().trim());
        publication.setContenu(request.contenu());
        if (request.imageUrl() != null) {
            publication.setImageUrl(request.imageUrl());
        }
        publication.setProduitLie(resolveProduct(request.produitLieId()));
        if (request.statut() != null) {
            publication.setStatut(request.statut());
        }
        if (request.misEnAvant() != null) {
            publication.setMisEnAvant(request.misEnAvant());
        }
        return PublicationResponse.from(publication);
    }

    @Transactional
    public void delete(Long id) {
        Publication publication = get(id);
        fileStorageService.deleteStoredImage(publication.getImageUrl());
        publicationRepository.delete(publication);
    }

    @Transactional
    public PublicationResponse uploadImage(Long id, MultipartFile file) {
        Publication publication = get(id);
        String previous = publication.getImageUrl();
        String stored = fileStorageService.storePublicationImage(id, file);
        fileStorageService.deleteStoredImage(previous);
        publication.setImageUrl(stored);
        return PublicationResponse.from(publication);
    }

    private Publication get(Long id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Publication introuvable"));
    }

    private Product resolveProduct(Long productId) {
        if (productId == null) {
            return null;
        }
        return productService.get(productId);
    }
}
