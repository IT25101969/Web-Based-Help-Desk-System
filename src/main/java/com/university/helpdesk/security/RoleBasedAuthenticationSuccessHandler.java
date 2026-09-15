package com.university.helpdesk.security;

import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.ActivityLogService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;

@Component
public class RoleBasedAuthenticationSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserAccountRepository userAccountRepository;
    private final ActivityLogService activityLogService;

    public RoleBasedAuthenticationSuccessHandler(
            UserAccountRepository userAccountRepository,
            ActivityLogService activityLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.activityLogService = activityLogService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        String universityId = authentication.getName();

        UserAccount user = userAccountRepository
                .findByUniversityId(universityId)
                .orElse(null);

        if (user != null) {

            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setLastLoginAt(LocalDateTime.now());

            userAccountRepository.save(user);

            activityLogService.log(
                    user,
                    "LOGIN_SUCCESS",
                    "USER_ACCOUNT",
                    user.getUserId(),
                    request.getRemoteAddr()
            );
        }

        Collection<? extends GrantedAuthority> authorities =
                authentication.getAuthorities();

        for (GrantedAuthority authority : authorities) {

            String role = authority.getAuthority();

            if ("ROLE_Student".equals(role)) {
                response.sendRedirect("/student/dashboard");
                return;
            }

            if ("ROLE_Help Desk Support Staff".equals(role)) {
                response.sendRedirect("/staff/dashboard");
                return;
            }

            if ("ROLE_Department Support Team Member".equals(role)) {
                response.sendRedirect("/staff/dashboard");
                return;
            }

            if ("ROLE_Department Manager".equals(role)) {
                response.sendRedirect("/manager/dashboard");
                return;
            }

            if ("ROLE_System Administrator".equals(role)) {
                response.sendRedirect("/admin/dashboard");
                return;
            }

            if ("ROLE_University Management".equals(role)) {
                response.sendRedirect("/management/dashboard");
                return;
            }
        }

        response.sendRedirect("/dashboard");
    }
}