package com.ndbshopping.backend.config;

import com.ndbshopping.backend.entity.Category;
import com.ndbshopping.backend.entity.CategoryAttributeDefinition;
import com.ndbshopping.backend.entity.Product;
import com.ndbshopping.backend.entity.Publication;
import com.ndbshopping.backend.entity.User;
import com.ndbshopping.backend.entity.enums.AttributeValueType;
import com.ndbshopping.backend.entity.enums.CategoryType;
import com.ndbshopping.backend.entity.enums.ProductSource;
import com.ndbshopping.backend.entity.enums.ProductStatus;
import com.ndbshopping.backend.entity.enums.PublicationStatus;
import com.ndbshopping.backend.entity.enums.Role;
import com.ndbshopping.backend.repository.CategoryAttributeDefinitionRepository;
import com.ndbshopping.backend.repository.CategoryRepository;
import com.ndbshopping.backend.repository.ProductRepository;
import com.ndbshopping.backend.repository.PublicationRepository;
import com.ndbshopping.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryAttributeDefinitionRepository attributeRepository;
    private final ProductRepository productRepository;
    private final PublicationRepository publicationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            CategoryAttributeDefinitionRepository attributeRepository,
            ProductRepository productRepository,
            PublicationRepository publicationRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.attributeRepository = attributeRepository;
        this.productRepository = productRepository;
        this.publicationRepository = publicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        if (categoryRepository.count() == 0) {
            seedCatalog();
        }
        seedDemoProducts();
    }

    private void seedAdmin() {
        if (userRepository.existsByTelephone("37565537")) {
            return;
        }
        // Bases déjà déployées : ne pas créer un second admin si l'ancien seed (20000001) ou un ADMIN existe.
        if (userRepository.existsByTelephone("20000001") || userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        userRepository.save(User.builder()
                .nom("Administrateur")
                .telephone("37565537")
                .passwordHash(passwordEncoder.encode("password123"))
                .telephoneVerifie(true)
                .role(Role.ADMIN)
                .build());
        log.info("Compte admin de démo créé (téléphone 37565537)");
    }

    private void seedCatalog() {
        Category vetements = categoryRepository.save(Category.builder()
                .nom("Vêtements")
                .type(CategoryType.PRODUIT)
                .build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(vetements).nomAttribut("taille").typeValeur(AttributeValueType.TEXTE).build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(vetements).nomAttribut("couleur").typeValeur(AttributeValueType.TEXTE).build());

        Category hotels = categoryRepository.save(Category.builder()
                .nom("Hôtels")
                .type(CategoryType.HOTEL)
                .build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(hotels).nomAttribut("nombre de chambres").typeValeur(AttributeValueType.NOMBRE).build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(hotels).nomAttribut("ville").typeValeur(AttributeValueType.TEXTE).build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(hotels).nomAttribut("prix/nuit").typeValeur(AttributeValueType.NOMBRE).build());

        Category voitures = categoryRepository.save(Category.builder()
                .nom("Voitures")
                .type(CategoryType.VOITURE)
                .build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(voitures).nomAttribut("marque").typeValeur(AttributeValueType.TEXTE).build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(voitures).nomAttribut("année").typeValeur(AttributeValueType.NOMBRE).build());
        attributeRepository.save(CategoryAttributeDefinition.builder()
                .category(voitures).nomAttribut("kilométrage").typeValeur(AttributeValueType.NOMBRE).build());

        productRepository.save(Product.builder()
                .nom("T-shirt coton")
                .description("T-shirt basique, idéal pour le quotidien à Nouadhibou.")
                .prix(new BigDecimal("1500.00"))
                .stock(40)
                .category(vetements)
                .sourceOrigine(ProductSource.MANUEL)
                .statut(ProductStatus.PUBLIE)
                .build());

        publicationRepository.save(Publication.builder()
                .titre("Bienvenue sur NDB SHOPPING")
                .contenu("Marketplace pour Nouadhibou. Commandez en toute simplicité.")
                .statut(PublicationStatus.PUBLIE)
                .build());

        log.info("Catégories et données de démo créées (Vêtements, Hôtels, Voitures)");
    }

    /**
     * Produits de vitrine : indépendant de l'admin et des catégories déjà présentes.
     * Idempotent produit par produit (existence par nom), pour un environnement déjà déployé.
     */
    private void seedDemoProducts() {
        seedProduct("Vêtements", "Boubou traditionnel homme",
                "Boubou ample en tissu de qualité, porté pour les fêtes et cérémonies à Nouadhibou.",
                "8500.00", 15);
        seedProduct("Vêtements", "Chemise homme manches longues",
                "Chemise légère à manches longues, adaptée au climat côtier de Nouadhibou.",
                "3200.00", 25);
        seedProduct("Vêtements", "Robe été femme",
                "Robe fluide pour l'été, confortable en ville comme au bord de mer.",
                "4500.00", 20);

        seedProduct("Hôtels", "Hôtel El Medina",
                "Hôtel au centre de Nouadhibou, chambres climatisées proches des commerces et du port.",
                "15000.00", 5);
        seedProduct("Hôtels", "Hôtel Nouadhibou Palace",
                "Établissement confortable à Nouadhibou, idéal pour un séjour d'affaires ou en famille.",
                "12000.00", 8);
        seedProduct("Hôtels", "Auberge du Port",
                "Auberge simple près du port de Nouadhibou, pratique pour les transitaires et les pêcheurs.",
                "6000.00", 10);

        seedProduct("Voitures", "Toyota Hilux 2020",
                "Pick-up Toyota Hilux 2020, robuste pour les pistes et le transport à Nouadhibou.",
                "4500000.00", 1);
        seedProduct("Voitures", "Peugeot 206 occasion",
                "Peugeot 206 d'occasion, citadine économique pour les déplacements en ville.",
                "1200000.00", 1);
        seedProduct("Voitures", "Renault Duster 2019",
                "Renault Duster 2019, SUV polyvalent pour la ville et les routes de la région.",
                "3200000.00", 1);
    }

    private void seedProduct(String categoryNom, String nom, String description, String prix, int stock) {
        if (productRepository.existsByNomIgnoreCase(nom)) {
            return;
        }
        Category category = categoryRepository.findFirstByNomIgnoreCase(categoryNom).orElse(null);
        if (category == null) {
            log.warn("Catégorie '{}' introuvable, produit '{}' non créé", categoryNom, nom);
            return;
        }
        productRepository.save(Product.builder()
                .nom(nom)
                .description(description)
                .prix(new BigDecimal(prix))
                .stock(stock)
                .category(category)
                .sourceOrigine(ProductSource.MANUEL)
                .statut(ProductStatus.PUBLIE)
                .build());
        log.info("Produit de démo créé : {} ({})", nom, categoryNom);
    }
}
