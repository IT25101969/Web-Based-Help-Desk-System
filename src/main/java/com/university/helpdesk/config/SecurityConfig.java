package com.university.helpdesk.config;

import com.university.helpdesk.security.CustomAuthenticationFailureHandler;
import com.university.helpdesk.security.RoleBasedAuthenticationSuccessHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

                        // Public authentication resources
                        .requestMatchers(
                                "/login",
                                "/forgot-password",
                                "/reset-password",
                                "/access-denied",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()

                        // Permission-level RBAC: student ticket submission/tracking
                        .requestMatchers(
                                HttpMethod.GET,
                                "/student/tickets/new"
                        ).hasAuthority("SUBMIT_TICKET")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/student/tickets"
                        ).hasAuthority("SUBMIT_TICKET")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/student/tickets/**"
                        ).hasAuthority("VIEW_OWN_TICKETS")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/student/tickets/*/comments",
                                "/student/tickets/*/feedback"
                        ).hasAuthority("VIEW_OWN_TICKETS")

                        // Permission-level RBAC: ticket handling
                        .requestMatchers(
                                HttpMethod.POST,
                                "/staff/tickets/*/assign",
                                "/manager/tickets/*/assign",
                                "/admin/tickets/*/assign"
                        ).hasAuthority("ASSIGN_TICKET")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/staff/tickets/*/status",
                                "/staff/tickets/*/comments",
                                "/manager/tickets/*/status",
                                "/manager/tickets/*/comments",
                                "/admin/tickets/*/status",
                                "/admin/tickets/*/comments"
                        ).hasAuthority("UPDATE_TICKET")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/staff/tickets/**",
                                "/manager/tickets/**",
                                "/admin/tickets/**"
                        ).hasAuthority("VIEW_ALL_TICKETS")

                        // Permission-level RBAC: administration
                        .requestMatchers("/admin/users/**")
                        .hasAuthority("MANAGE_USERS")

                        .requestMatchers("/admin/faqs/**")
                        .hasAuthority("MANAGE_FAQ")

                        .requestMatchers(
                                "/admin/reports/**",
                                "/management/reports/**"
                        ).hasAuthority("VIEW_REPORTS")

                        // Role-level area boundaries
                        .requestMatchers("/student/**")
                        .hasRole("Student")

                        .requestMatchers("/staff/**")
                        .hasAnyRole(
                                "Help Desk Support Staff",
                                "Department Support Team Member"
                        )

                        .requestMatchers("/manager/**")
                        .hasRole("Department Manager")

                        .requestMatchers("/admin/**")
                        .hasRole("System Administrator")

                        .requestMatchers("/management/**")
                        .hasRole("University Management")

                        // Shared authenticated pages such as FAQ and notifications
                        .anyRequest()
                        .authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("login")
                        .passwordParameter("password")
                        .successHandler(successHandler)
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

                .sessionManagement(session ->
                        session.sessionFixation()
                                .migrateSession()
                );

        return http.build();
    }
}
