package com.ndbshopping.backend.controller.admin;

import com.ndbshopping.backend.dto.admin.AdminUserResponse;
import com.ndbshopping.backend.dto.admin.CreateUserRequest;
import com.ndbshopping.backend.dto.admin.UpdateUserRoleRequest;
import com.ndbshopping.backend.dto.admin.UpdateUserStatusRequest;
import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Comptes")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des utilisateurs")
    public PageResponse<AdminUserResponse> list(
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return adminUserService.list(role, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un compte (vérifié, sans OTP)")
    public AdminUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return adminUserService.create(request);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Modifier le rôle d'un autre utilisateur")
    public AdminUserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequest request) {
        return adminUserService.updateRole(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activer ou désactiver un compte")
    public AdminUserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminUserService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un utilisateur sans commandes")
    public void delete(@PathVariable Long id) {
        adminUserService.delete(id);
    }
}
