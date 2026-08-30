package com.ndbshopping.backend.controller;

import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.dto.publication.PublicationResponse;
import com.ndbshopping.backend.service.PublicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/publications")
@Tag(name = "Publications (public)")
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping
    @Operation(summary = "Publications publiées")
    public PageResponse<PublicationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return publicationService.listPublic(pageable);
    }

    @GetMapping("/mises-en-avant")
    @Operation(summary = "Publications mises en avant (bandeau / carrousel, max 8)")
    public List<PublicationResponse> misesEnAvant() {
        return publicationService.listMisesEnAvant();
    }
}
