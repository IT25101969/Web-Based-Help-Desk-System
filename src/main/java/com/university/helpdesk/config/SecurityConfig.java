package com.university.helpdesk.config;

import com.university.helpdesk.security.CustomAuthenticationFailureHandler;
import com.university.helpdesk.security.RoleBasedAuthenticationSuccessHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RoleBasedAuthenticationSuccessHandler successHandler,
            CustomAuthenticationFailureHandler failureHandler
    ) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth

                        // Public pages
                        .requestMatchers(
                                "/login",
                                "/forgot-password",
                                "/reset-password",
                                "/access-denied",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()

                        // Student
                        .requestMatchers("/student/**")
                        .hasRole("Student")

                        // Support Staff
                        .requestMatchers("/staff/**")
                        .hasAnyRole(
                                "Help Desk Support Staff",
                                "Department Support Team Member"
                        )

                        // Department Manager
                        .requestMatchers("/manager/**")
                        .hasRole("Department Manager")

                        // System Administrator
                        .requestMatchers("/admin/**")
                        .hasRole("System Administrator")

                        // University Management
                        .requestMatchers("/management/**")
                        .hasRole("University Management")

                        // Other pages need authentication
                        .anyRequest()
                        .authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("login")
                        .passwordParameter("password")

                        // Successful login
                        .successHandler(successHandler)

                        // Failed login
                        .failureHandler(failureHandler)

                        .permitAll()
                )

                .exceptionHandling(exception ->
                        exception.accessDeniedPage("/access-denied")
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                // Secure session against session fixation
                .sessionManagement(session ->
                        session.sessionFixation()
                                .migrateSession()
                );

        return http.build();
    }
}