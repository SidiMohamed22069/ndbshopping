package com.ndbshopping.backend.service;

import com.ndbshopping.backend.dto.admin.AdminUserResponse;
import com.ndbshopping.backend.dto.admin.CreateUserRequest;
import com.ndbshopping.backend.dto.admin.UpdateUserRoleRequest;
import com.ndbshopping.backend.dto.admin.UpdateUserStatusRequest;
import com.ndbshopping.backend.dto.common.PageResponse;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.CartItemRepository;
import com.ndbshopping.backend.repository.OrderRepository;
import com.ndbshopping.backend.repository.UserRepository;
import com.ndbshopping.backend.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AdminUserService(
            UserRepository userRepository,
            OrderRepository orderRepository,
            CartItemRepository cartItemRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(Role role, Pageable pageable) {
        Page<User> page = role == null
                ? userRepository.findAllByOrderByCreatedAtDesc(pageable)
                : userRepository.findByRoleOrderByCreatedAtDesc(role, pageable);
        return PageResponse.from(page.map(AdminUserResponse::from));
    }

    @Transactional
    public AdminUserResponse create(CreateUserRequest request) {
        if (userRepository.existsByTelephone(request.telephone())) {
            throw ApiException.conflict("Ce numéro de téléphone est déjà utilisé");
        }
        User user = User.builder()
                .nom(request.nom().trim())
                .telephone(request.telephone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .telephoneVerifie(true)
                .role(request.role())
                .actif(true)
                .build();
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateRole(Long id, UpdateUserRoleRequest request) {
        User current = currentUserService.requireUser();
        User target = get(id);
        if (current.getId().equals(target.getId())) {
            throw ApiException.badRequest("Vous ne pouvez pas modifier votre propre rôle");
        }
        target.setRole(request.role());
        return AdminUserResponse.from(target);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        User current = currentUserService.requireUser();
        User target = get(id);
        if (current.getId().equals(target.getId())) {
            throw ApiException.badRequest("Vous ne pouvez pas désactiver votre propre compte");
        }
        target.setActif(request.actif());
        return AdminUserResponse.from(target);
    }

    /**
     * Suppression refusée si l'utilisateur a des commandes (historique conservé).
     * Le panier (sans commande) est vidé puis le compte est supprimé.
     */
    @Transactional
    public void delete(Long id) {
        User current = currentUserService.requireUser();
        User target = get(id);
        if (current.getId().equals(target.getId())) {
            throw ApiException.badRequest("Vous ne pouvez pas supprimer votre propre compte");
        }
        if (orderRepository.existsByUserId(id)) {
            throw ApiException.conflict("Impossible de supprimer un utilisateur ayant des commandes");
        }
        cartItemRepository.deleteByUserId(id);
        userRepository.delete(target);
    }

    private User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }
}
