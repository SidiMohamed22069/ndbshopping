package com.ndbshopping.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migre les lignes déjà en base avec l'ancienne valeur d'enum Role::CLIENT
 * (renommée en USER). Doit s'exécuter après EnumCheckConstraintDropper : sans
 * ça, la contrainte CHECK générée par Hibernate pour l'ancien enum (qui
 * n'autorise que CLIENT/ADMIN) rejetterait cet UPDATE vers 'USER'.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RoleMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public RoleMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE role = 'CLIENT'");
            if (updated > 0) {
                log.info("Migration rôle : {} utilisateur(s) CLIENT -> USER", updated);
            }
        } catch (Exception ex) {
            log.warn("Migration du rôle CLIENT -> USER impossible : {}", ex.getMessage());
        }
    }
}
