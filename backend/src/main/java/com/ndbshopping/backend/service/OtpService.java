package com.ndbshopping.backend.service;

import com.ndbshopping.backend.client.ChinguisoftSmsClient;
import com.ndbshopping.backend.client.ChinguisoftSmsResponse;
import com.ndbshopping.backend.config.AppProperties;
import com.ndbshopping.backend.entity.enums.NotificationType;
import com.ndbshopping.backend.exception.ApiException;
import com.ndbshopping.backend.exception.SmsBalanceDepletedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
@Slf4j
public class OtpService {

    private final StringRedisTemplate redis;
    private final ChinguisoftSmsClient smsClient;
    private final NotificationService notificationService;
    private final AppProperties appProperties;

    public OtpService(
            StringRedisTemplate redis,
            ChinguisoftSmsClient smsClient,
            NotificationService notificationService,
            AppProperties appProperties
    ) {
        this.redis = redis;
        this.smsClient = smsClient;
        this.notificationService = notificationService;
        this.appProperties = appProperties;
    }

    public void sendOtp(String telephone, String lang) {
        AppProperties.Otp otpCfg = appProperties.otp();
        String sendKey = "otp:send:" + telephone;
        Long sends = redis.opsForValue().increment(sendKey);
        if (sends != null && sends == 1L) {
            redis.expire(sendKey, Duration.ofMinutes(otpCfg.sendWindowMinutes()));
        }
        if (sends != null && sends > otpCfg.maxSendPerWindow()) {
            throw ApiException.tooManyRequests("Trop de demandes de code. Réessayez plus tard.");
        }

        String code;
        AppProperties.Chinguisoft smsCfg = appProperties.chinguisoft();
        if (smsCfg.mockEnabled()) {
            code = smsCfg.mockCode();
            log.warn("SMS mock activé — code OTP {} pour {}", code, telephone);
        } else {
            if (smsCfg.validationKey() == null || smsCfg.validationKey().isBlank()
                    || smsCfg.token() == null || smsCfg.token().isBlank()) {
                throw ApiException.serviceUnavailable("Service SMS non configuré");
            }
            try {
                ChinguisoftSmsResponse response = smsClient.sendValidationSms(telephone, lang);
                code = String.valueOf(response.code());
                Integer balance = response.balance();
                if (balance != null && balance < smsCfg.lowBalanceThreshold()) {
                    notificationService.createAndPush(
                            NotificationType.SOLDE_SMS_BAS,
                            "Solde SMS Chinguisoft bas : " + balance + " (seuil " + smsCfg.lowBalanceThreshold() + ")",
                            null
                    );
                }
            } catch (SmsBalanceDepletedException ex) {
                notificationService.createAndPush(
                        NotificationType.SOLDE_SMS_BAS,
                        "Solde SMS épuisé — rechargez le compte Chinguisoft",
                        null
                );
                throw ex;
            }
        }

        redis.opsForValue().set(otpKey(telephone), code, Duration.ofMinutes(otpCfg.ttlMinutes()));
        redis.delete(attemptsKey(telephone));
    }

    public void verifyOrThrow(String telephone, String code) {
        AppProperties.Otp otpCfg = appProperties.otp();
        String stored = redis.opsForValue().get(otpKey(telephone));
        if (stored == null) {
            throw ApiException.badRequest("Code expiré, redemandez un code");
        }

        String attemptsKey = attemptsKey(telephone);
        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redis.expire(attemptsKey, Duration.ofMinutes(otpCfg.ttlMinutes()));
        }
        if (attempts != null && attempts > otpCfg.maxVerifyAttempts()) {
            redis.delete(otpKey(telephone));
            throw ApiException.tooManyRequests("Trop de tentatives. Redemandez un code.");
        }

        if (!Objects.equals(stored, code.trim())) {
            throw ApiException.badRequest("Code incorrect");
        }

        redis.delete(otpKey(telephone));
        redis.delete(attemptsKey);
    }

    private static String otpKey(String telephone) {
        return "otp:" + telephone;
    }

    private static String attemptsKey(String telephone) {
        return "otp:attempts:" + telephone;
    }
}
