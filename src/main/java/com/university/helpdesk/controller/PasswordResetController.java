package com.university.helpdesk.controller;

import com.university.helpdesk.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @Value("${app.security.show-reset-link:true}")
    private boolean showResetLink;

    public PasswordResetController(
            PasswordResetService passwordResetService
    ) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(
            @RequestParam("login") String login,
            HttpServletRequest request,
            Model model
    ) {

        Optional<String> resetToken =
                passwordResetService.createResetToken(
                        login.trim(),
                        request.getRemoteAddr()
                );

        model.addAttribute(
                "message",
                "If a matching active account exists, a password reset request has been created."
        );

        if (showResetLink) {
            resetToken.ifPresent(token ->
                    model.addAttribute(
                            "resetLink",
                            "/reset-password?token=" + token
                    )
            );
        }

        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(
            @RequestParam(value = "token", required = false) String token,
            Model model
    ) {

        if (!passwordResetService.isValidToken(token)) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpServletRequest request,
            Model model
    ) {

        if (password.length() < 8) {
            model.addAttribute(
                    "error",
                    "Password must contain at least 8 characters."
            );
            model.addAttribute("token", token);
            return "reset-password";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute(
                    "error",
                    "Password and confirmation do not match."
            );
            model.addAttribute("token", token);
            return "reset-password";
        }

        boolean resetSuccessful =
                passwordResetService.resetPassword(
                        token,
                        password,
                        request.getRemoteAddr()
                );

        if (!resetSuccessful) {
            model.addAttribute("invalidToken", true);
            return "reset-password";
        }

        return "redirect:/login?reset";
    }
}
