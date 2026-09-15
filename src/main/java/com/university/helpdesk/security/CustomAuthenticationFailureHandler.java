package com.university.helpdesk.security;

import com.university.helpdesk.entity.AccountStatus;
import com.university.helpdesk.entity.UserAccount;
import com.university.helpdesk.repository.UserAccountRepository;
import com.university.helpdesk.service.ActivityLogService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class CustomAuthenticationFailureHandler
        implements AuthenticationFailureHandler {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 15;

    private final UserAccountRepository userAccountRepository;
    private final ActivityLogService activityLogService;

    public CustomAuthenticationFailureHandler(
            UserAccountRepository userAccountRepository,
            ActivityLogService activityLogService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.activityLogService = activityLogService;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        String login = request.getParameter("login");

        Optional<UserAccount> optionalUser =
                userAccountRepository.findByUniversityIdOrEmail(login, login);

        if (optionalUser.isPresent()) {

            UserAccount user = optionalUser.get();

            if (user.getAccountStatus() == AccountStatus.DISABLED) {

                activityLogService.log(
                        user,
                        "LOGIN_DENIED_DISABLED_ACCOUNT",
                        "USER_ACCOUNT",
                        user.getUserId(),
                        request.getRemoteAddr()
                );

                response.sendRedirect("/login?disabled");
                return;
            }

            if (user.getAccountStatus() == AccountStatus.LOCKED
                    && user.getLockedUntil() != null
                    && user.getLockedUntil().isAfter(LocalDateTime.now())) {

                activityLogService.log(
                        user,
                        "LOGIN_DENIED_LOCKED_ACCOUNT",
                        "USER_ACCOUNT",
                        user.getUserId(),
                        request.getRemoteAddr()
                );

                response.sendRedirect("/login?locked");
                return;
            }

            if (user.getAccountStatus() == AccountStatus.LOCKED
                    && user.getLockedUntil() != null
                    && user.getLockedUntil().isBefore(LocalDateTime.now())) {

                user.setAccountStatus(AccountStatus.ACTIVE);
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);

                userAccountRepository.save(user);
            }

            if (exception instanceof BadCredentialsException) {

                int failedAttempts =
                        user.getFailedLoginAttempts() == null
                                ? 0
                                : user.getFailedLoginAttempts();

                failedAttempts++;

                user.setFailedLoginAttempts(failedAttempts);

                activityLogService.log(
                        user,
                        "LOGIN_FAILED",
                        "USER_ACCOUNT",
                        user.getUserId(),
                        request.getRemoteAddr()
                );

                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {

                    user.setAccountStatus(AccountStatus.LOCKED);
                    user.setLockedUntil(
                            LocalDateTime.now()
                                    .plusMinutes(LOCK_TIME_MINUTES)
                    );

                    userAccountRepository.save(user);

                    activityLogService.log(
                            user,
                            "ACCOUNT_LOCKED",
                            "USER_ACCOUNT",
                            user.getUserId(),
                            request.getRemoteAddr()
                    );

                    response.sendRedirect("/login?locked");
                    return;
                }

                userAccountRepository.save(user);
            }
        }

        response.sendRedirect("/login?error");
    }
}