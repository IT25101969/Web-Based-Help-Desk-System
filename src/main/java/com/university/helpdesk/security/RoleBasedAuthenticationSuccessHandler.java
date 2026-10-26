package com.university.helpdesk.security;

import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.ActivityLogService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

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

        response.sendRedirect(request.getContextPath() + RoleDestinations.basePath(authentication) + "/dashboard");
    }
}
