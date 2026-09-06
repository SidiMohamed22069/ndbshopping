package com.ndbshopping.backend.entity.enums;

public enum NotificationType {
    NOUVELLE_COMMANDE,
    PRODUIT_A_VALIDER,
    NEGOCIATION_PRIX,
    SOLDE_SMS_BAS,
    AUTRE,
    /** Ci-dessous : notifications privées poussées à un client (pas à la boîte admin). */
    NEGOCIATION_REPONSE,
    COMMANDE_STATUT
}
