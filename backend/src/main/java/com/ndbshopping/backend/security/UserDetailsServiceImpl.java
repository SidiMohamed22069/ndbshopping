package com.ndbshopping.backend.security;

import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String telephone) throws UsernameNotFoundException {
        User user = userRepository.findByTelephone(telephone)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getTelephone())
                .password(user.getPasswordHash())
                .disabled(!user.isActif())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
