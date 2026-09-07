package com.ndbshopping.backend.service;

import com.ndbshopping.backend.exception.ApiException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Garde-fou anti-SSRF pour l'import de produits par URL : un admin colle une URL
 * arbitraire et le serveur va la récupérer lui-même, il faut donc l'empêcher de
 * cibler le réseau interne (localhost, IP privées, métadonnées cloud 169.254.169.254...).
 */
final class UrlSafetyGuard {

    private UrlSafetyGuard() {
    }

    static void assertSafe(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw ApiException.badRequest("Seules les URL http:// ou https:// sont autorisées");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw ApiException.badRequest("URL invalide");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw ApiException.badRequest("Nom d'hôte introuvable");
        }
        if (addresses.length == 0) {
            throw ApiException.badRequest("Nom d'hôte introuvable");
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw ApiException.badRequest("Cette adresse n'est pas autorisée pour l'import");
            }
        }
    }
}
