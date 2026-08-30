package com.ndbshopping.backend.security;

import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser() {
        return findUser().orElseThrow(() -> ApiException.unauthorized("Authentification requise"));
    }

    public Optional<User> findUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails details)) {
            return Optional.empty();
        }
        return userRepository.findByTelephone(details.getUsername());
    }
}
