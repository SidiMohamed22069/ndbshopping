package com.ndbshopping.backend.client;

import com.ndbshopping.backend.config.AppProperties;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.exception.SmsBalanceDepletedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Client HTTP pour l'API SMS Validation Chinguisoft.
 * Chinguisoft génère et envoie le code ; c'est à nous de le stocker (Redis) et de le vérifier.
 */
@Component
@Slf4j
public class ChinguisoftSmsClient {

    private final RestClient restClient;
    private final AppProperties appProperties;

    public ChinguisoftSmsClient(RestClient restClient, AppProperties appProperties) {
        this.restClient = restClient;
        this.appProperties = appProperties;
    }

    public ChinguisoftSmsResponse sendValidationSms(String phone, String lang) {
        AppProperties.Chinguisoft cfg = appProperties.chinguisoft();
        String url = cfg.baseUrl() + "/" + cfg.validationKey();

        try {
            return doSend(url, cfg.token(), phone, lang);
        } catch (SmsBalanceDepletedException ex) {
            throw ex;
        } catch (ApiException ex) {
            if (ex.getStatus().value() == 503) {
                log.warn("Chinguisoft 503 — nouvel essai dans 2 secondes");
                sleepQuietly(2000);
                return doSend(url, cfg.token(), phone, lang);
            }
            throw ex;
        }
    }

    private ChinguisoftSmsResponse doSend(String url, String token, String phone, String lang) {
        try {
            ChinguisoftSmsResponse response = restClient.post()
                    .uri(url)
                    .header("Validation-token", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("phone", phone, "lang", lang))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        throw mapStatus(status);
                    })
                    .body(ChinguisoftSmsResponse.class);

            if (response == null || response.code() == null) {
                throw ApiException.serviceUnavailable("Réponse SMS invalide");
            }
            return response;
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapStatus(ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Erreur d'appel Chinguisoft", ex);
            throw ApiException.serviceUnavailable("Service SMS temporairement indisponible");
        }
    }

    private static ApiException mapStatus(int status) {
        return switch (status) {
            case 422 -> ApiException.badRequest("Numéro ou langue invalide pour l'envoi SMS");
            case 401 -> {
                log.error("Token Chinguisoft invalide — vérifier SMS_TOKEN");
                yield ApiException.serviceUnavailable("Configuration SMS invalide");
            }
            case 402 -> new SmsBalanceDepletedException();
            case 429 -> ApiException.tooManyRequests("Trop de requêtes SMS, réessayez dans quelques minutes");
            case 503 -> ApiException.serviceUnavailable("Service SMS temporairement indisponible");
            default -> ApiException.serviceUnavailable("Erreur du service SMS (" + status + ")");
        };
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.serviceUnavailable("Service SMS temporairement indisponible");
        }
    }
}
