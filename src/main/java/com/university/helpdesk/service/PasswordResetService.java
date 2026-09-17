package com.university.helpdesk.service;

import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.PasswordResetToken;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.PasswordResetTokenRepository;
import com.university.helpdesk.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int TOKEN_EXPIRY_MINUTES = 30;

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final com.university.helpdesk.repository.EmailNotificationQueueRepository emailQueueRepository;
    private final String publicUrl;

    public PasswordResetService(
            UserAccountRepository userAccountRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ActivityLogService activityLogService,
            com.university.helpdesk.repository.EmailNotificationQueueRepository emailQueueRepository,
            @org.springframework.beans.factory.annotation.Value("${app.public-url:http://localhost:8080}") String publicUrl
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.emailQueueRepository = emailQueueRepository;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Optional<String> createResetToken(String login, String ipAddress) {

        Optional<UserAccount> userOptional =
                userAccountRepository.findByUniversityIdOrEmail(login, login);

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        UserAccount user = userOptional.get();

        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            activityLogService.log(
                    user,
                    "PASSWORD_RESET_DENIED_DISABLED_ACCOUNT",
                    "USER_ACCOUNT",
                    user.getUserId(),
                    ipAddress
            );
            return Optional.empty();
        }

        invalidateActiveTokens(user.getUserId());

        String rawToken = generateRawToken();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
        token.setUsed(false);

        tokenRepository.save(token);

        com.university.helpdesk.entity.EmailNotificationQueue email = new com.university.helpdesk.entity.EmailNotificationQueue();
        email.setUser(user);
        email.setRecipientEmail(user.getEmail());
        email.setSubject("University Help Desk password reset");
        email.setMessage("Reset your password within 30 minutes: " + publicUrl + "/reset-password?token=" + rawToken
                + "\nIf you did not request this, ignore this message.");
        emailQueueRepository.save(email);

        activityLogService.log(
                user,
                "PASSWORD_RESET_REQUESTED",
                "USER_ACCOUNT",
                user.getUserId(),
                ipAddress
        );

        return Optional.of(rawToken);
    }

    @Transactional(readOnly = true)
    public boolean isValidToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        return tokenRepository
                .findByTokenHashAndUsedFalse(hashToken(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional
    public boolean resetPassword(
            String rawToken,
            String newPassword,
            String ipAddress
    ) {

        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        Optional<PasswordResetToken> tokenOptional =
                tokenRepository.findUnusedForUpdate(hashToken(rawToken));

        if (tokenOptional.isEmpty()) {
            return false;
        }

        PasswordResetToken token = tokenOptional.get();

        if (!token.getExpiresAt().isAfter(LocalDateTime.now())) {
            token.setUsed(true);
            token.setUsedAt(LocalDateTime.now());
            tokenRepository.save(token);
            return false;
        }

        UserAccount user = token.getUser();

        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            activityLogService.log(
                    user,
                    "PASSWORD_RESET_DENIED_DISABLED_ACCOUNT",
                    "USER_ACCOUNT",
                    user.getUserId(),
                    ipAddress
            );
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            user.setAccountStatus(AccountStatus.ACTIVE);
        }

        userAccountRepository.save(user);

        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        invalidateActiveTokens(user.getUserId());

        activityLogService.log(
                user,
                "PASSWORD_RESET_COMPLETED",
                "USER_ACCOUNT",
                user.getUserId(),
                ipAddress
        );

        return true;
    }

    private void invalidateActiveTokens(Long userId) {

        List<PasswordResetToken> activeTokens =
                tokenRepository.findByUserUserIdAndUsedFalse(userId);

        LocalDateTime now = LocalDateTime.now();

        for (PasswordResetToken token : activeTokens) {
            token.setUsed(true);
            token.setUsedAt(now);
        }

        if (!activeTokens.isEmpty()) {
            tokenRepository.saveAll(activeTokens);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder builder = new StringBuilder();

            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }

            return builder.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    e
            );
        }
    }
}
