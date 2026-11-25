package com.termiapislabs.auth;

import com.termiapislabs.exception.ApiException;
import com.termiapislabs.model.AuthCode;
import com.termiapislabs.model.AuthCodeType;
import com.termiapislabs.model.User;
import com.termiapislabs.repository.AuthCodeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailCodeService {
    private final AuthCodeRepository authCodeRepository;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();
    private final byte[] hashSecret;
    private final long expirationMinutes;
    private final long resendCooldownSeconds;

    public EmailCodeService(AuthCodeRepository authCodeRepository, EmailService emailService,
                            @Value("${app.auth-code.secret}") String hashSecret,
                            @Value("${app.auth-code.expiration-minutes}") long expirationMinutes,
                            @Value("${app.auth-code.resend-cooldown-seconds}") long resendCooldownSeconds) {
        if (hashSecret == null || hashSecret.length() < 32) {
            throw new IllegalStateException("APP.AUTH-CODE.SECRET deve possuir pelo menos 32 caracteres.");
        }
        this.authCodeRepository = authCodeRepository;
        this.emailService = emailService;
        this.hashSecret = hashSecret.getBytes(StandardCharsets.UTF_8);
        this.expirationMinutes = expirationMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String hashCode(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível proteger o código.", exception);
        }
    }

    @Transactional
    public void issueAndSend(User user, AuthCodeType type, boolean enforceCooldown) {
        if (enforceCooldown) {
            var latest = authCodeRepository.findTopByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), type);
            if (latest.isPresent() && Instant.now().isBefore(
                latest.get().getCreatedAt().plusSeconds(resendCooldownSeconds))) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Aguarde antes de solicitar outro código.");
            }
        }

        authCodeRepository.findByUserIdAndTypeAndInvalidatedAtIsNull(user.getId(), type)
            .forEach(code -> code.setInvalidatedAt(Instant.now()));

        String code = generateCode();
        AuthCode authCode = new AuthCode();
        authCode.setUserId(user.getId());
        authCode.setType(type);
        authCode.setCodeHash(hashCode(code));
        authCode.setExpiresAt(Instant.now().plusSeconds(expirationMinutes * 60));
        authCodeRepository.save(authCode);

        if (type == AuthCodeType.EMAIL_CONFIRMATION) {
            emailService.sendConfirmationCode(user.getEmail(), code);
        } else {
            emailService.sendPasswordResetCode(user.getEmail(), code);
        }
    }

    @Transactional
    public AuthCode validateAndConsume(User user, AuthCodeType type, String code) {
        String suppliedHash = hashCode(code);
        var authCode = authCodeRepository.findTopByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), type)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Código inválido ou expirado."));

        boolean hashMatches = MessageDigest.isEqual(
            authCode.getCodeHash().getBytes(StandardCharsets.UTF_8),
            suppliedHash.getBytes(StandardCharsets.UTF_8));

        if (!hashMatches || !authCode.isUsable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Código inválido ou expirado.");
        }

        authCode.setUsedAt(Instant.now());
        authCode.setInvalidatedAt(Instant.now());
        return authCodeRepository.save(authCode);
    }
}
