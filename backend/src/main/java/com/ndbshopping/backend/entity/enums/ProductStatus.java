package com.ndbshopping.backend.entity.enums;

public enum ProductStatus {
    BROUILLON,
    EN_ATTENTE,
    PUBLIE,
    REJETE,
    /** Vendu : hors catalogue public, visible dans l'historique du propriétaire. */
    VENDU,
    /** Retiré sans vente (indisponible, erreur) : hors catalogue public, réactivable. */
    ARCHIVE
}
