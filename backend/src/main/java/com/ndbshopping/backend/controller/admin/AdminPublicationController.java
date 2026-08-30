package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.publication.PublicationRequest;
import com.ndbshopping.backend.dto.publication.PublicationResponse;
import com.ndbshopping.backend.service.PublicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/publications")
@Tag(name = "Admin — Publications")
public class AdminPublicationController {

    private final PublicationService publicationService;

    public AdminPublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping
    public PageResponse<PublicationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return publicationService.listAdmin(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse create(@Valid @RequestBody PublicationRequest request) {
        return publicationService.create(request);
    }

    @PutMapping("/{id}")
    public PublicationResponse update(@PathVariable Long id, @Valid @RequestBody PublicationRequest request) {
        return publicationService.update(id, request);
    }

    @PatchMapping("/{id}")
    public PublicationResponse patch(@PathVariable Long id, @Valid @RequestBody PublicationRequest request) {
        return publicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        publicationService.delete(id);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de l'image de publication (jpg/png/webp, max 5 Mo)")
    public PublicationResponse uploadImage(@PathVariable Long id, @RequestParam("image") MultipartFile image) {
        return publicationService.uploadImage(id, image);
    }
}
