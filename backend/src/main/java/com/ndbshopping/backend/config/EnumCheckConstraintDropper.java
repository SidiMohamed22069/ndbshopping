package com.ndbshopping.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Option A : retire les CHECK SQL que Hibernate génère sur les colonnes d'enum.
 * Ces contraintes ne sont pas mises à jour par ddl-auto=update quand on ajoute
 * une valeur d'enum, ce qui casse la prod (ex. products_statut_check).
 * La validation reste l'enum Java. Exécuté après le schéma Hibernate, à chaque démarrage.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class EnumCheckConstraintDropper implements ApplicationRunner {

    private static final Set<String> ENUM_TABLES = Set.of(
            "products",
            "orders",
            "users",
            "categories",
            "notifications",
            "publications",
            "category_attribute_definitions"
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public EnumCheckConstraintDropper(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> constraints = findCheckConstraints();
        if (constraints.isEmpty()) {
            return;
        }
        constraints.forEach((constraint, table) -> {
            try {
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
                log.info("Contrainte CHECK retirée : {}.{}", table, constraint);
            } catch (Exception ex) {
                log.warn("Impossible de retirer la contrainte CHECK {}.{} : {}", table, constraint, ex.getMessage());
            }
        });
    }

    private Map<String, String> findCheckConstraints() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String product = meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (product.contains("postgresql")) {
                return findPostgresChecks();
            }
            return findInformationSchemaChecks();
        } catch (Exception ex) {
            log.warn("Lecture des contraintes CHECK impossible : {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> findPostgresChecks() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT c.relname AS table_name, con.conname AS constraint_name
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE con.contype = 'c'
                  AND n.nspname = current_schema()
                """);
        return indexByTable(rows);
    }

    private Map<String, String> findInformationSchemaChecks() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT TABLE_NAME AS table_name, CONSTRAINT_NAME AS constraint_name
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_TYPE = 'CHECK'
                """);
        return indexByTable(rows);
    }

    private static Map<String, String> indexByTable(List<Map<String, Object>> rows) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String table = String.valueOf(row.get("table_name"));
            String constraint = String.valueOf(row.get("constraint_name"));
            if (ENUM_TABLES.contains(table.toLowerCase(Locale.ROOT))) {
                result.put(constraint, table);
            }
        }
        return result;
    }
}
