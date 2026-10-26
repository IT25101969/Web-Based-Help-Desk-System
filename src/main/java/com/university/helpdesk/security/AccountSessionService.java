package com.university.helpdesk.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

@Service
public class AccountSessionService {
    private final SessionRegistry registry;
    public AccountSessionService(SessionRegistry registry) { this.registry = registry; }

    public void expire(String universityId) {
        for (Object principal : registry.getAllPrincipals()) {
            if (principal instanceof UserDetails user && user.getUsername().equals(universityId)) {
                registry.getAllSessions(principal, false).forEach(session -> session.expireNow());
            }
        }
    }
}
