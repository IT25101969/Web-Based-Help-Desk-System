package com.university.helpdesk.service;

import com.university.helpdesk.repository.*;
import com.university.helpdesk.security.AccountSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserProfileService {
    private final UserAccountRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ActivityLogService logs;
    private final AccountSessionService sessions;
    public UserProfileService(UserAccountRepository users, PasswordResetTokenRepository tokens,
                              ActivityLogService logs, AccountSessionService sessions) {
        this.users = users; this.tokens = tokens; this.logs = logs; this.sessions = sessions;
    }

    @Transactional
    public void update(Long id, String universityId, String email, String firstName, String lastName, String actorId, String ip) {
        String cleanId = required(universityId, 50, "University ID");
        String cleanEmail = required(email, 150, "Email");
        String cleanFirst = required(firstName, 100, "First name");
        String cleanLast = required(lastName, 100, "Last name");
        if (!cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        var user = users.findForProfileUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User was not found."));
        var actor = users.findByUniversityId(actorId).orElseThrow(() -> new IllegalArgumentException("Administrator was not found."));
        if (users.existsByUniversityIdIgnoreCaseAndUserIdNot(cleanId, id)) throw new IllegalArgumentException("University ID already belongs to another account.");
        if (users.existsByEmailIgnoreCaseAndUserIdNot(cleanEmail, id)) throw new IllegalArgumentException("Email already belongs to another account.");
        if (users.existsByEmailIgnoreCaseAndUserIdNot(cleanId, id) || users.existsByUniversityIdIgnoreCaseAndUserIdNot(cleanEmail, id))
            throw new IllegalArgumentException("These login details conflict with another account.");
        String oldId = user.getUniversityId();
        boolean identityChanged = !oldId.equals(cleanId) || !user.getEmail().equals(cleanEmail);
        user.setUniversityId(cleanId); user.setEmail(cleanEmail); user.setFirstName(cleanFirst); user.setLastName(cleanLast);
        users.saveAndFlush(user);
        logs.log(actor, "USER_PROFILE_UPDATED", "USER_ACCOUNT", id, ip);
        if (identityChanged) {
            tokens.findByUserUserIdAndUsedFalse(id).forEach(token -> token.setUsed(true));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { sessions.expire(oldId); }
            });
        }
    }

    private String required(String value, int max, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        String clean = value.trim();
        if (clean.length() > max) throw new IllegalArgumentException(label + " must be " + max + " characters or fewer.");
        if (clean.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(label + " contains invalid characters.");
        return clean;
    }
}
