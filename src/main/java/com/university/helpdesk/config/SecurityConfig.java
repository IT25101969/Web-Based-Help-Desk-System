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
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

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

                        // Student ticket submission: role + permission
                        .requestMatchers(
                                HttpMethod.GET,
                                "/student/tickets/new"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('Student') and hasAuthority('SUBMIT_TICKET')"
                        ))

                        .requestMatchers(
                                HttpMethod.POST,
                                "/student/tickets"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('Student') and hasAuthority('SUBMIT_TICKET')"
                        ))

                        // Student-owned ticket tracking/replies/feedback
                        .requestMatchers(
                                HttpMethod.GET,
                                "/student/tickets/**"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('Student') and hasAuthority('VIEW_OWN_TICKETS')"
                        ))

                        .requestMatchers(
                                HttpMethod.POST,
                                "/student/tickets/*/comments",
                                "/student/tickets/*/feedback"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('Student') and hasAuthority('VIEW_OWN_TICKETS')"
                        ))

                        // Help Desk / Department Support ticket handling
                        .requestMatchers(
                                HttpMethod.POST,
                                "/staff/tickets/*/assign"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasAnyRole('Help Desk Support Staff','Department Support Team Member') " +
                                        "and hasAuthority('ASSIGN_TICKET')"
                        ))

                        .requestMatchers(
                                HttpMethod.POST,
                                "/staff/tickets/*/status",
                                "/staff/tickets/*/comments"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasAnyRole('Help Desk Support Staff','Department Support Team Member') " +
                                        "and hasAuthority('UPDATE_TICKET')"
                        ))

                        .requestMatchers(
                                HttpMethod.GET,
                                "/staff/tickets/**"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasAnyRole('Help Desk Support Staff','Department Support Team Member') " +
                                        "and hasAuthority('VIEW_ALL_TICKETS')"
                        ))

                        // Department Manager ticket handling
                        .requestMatchers(
                                HttpMethod.POST,
                                "/manager/tickets/*/assign"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('Department Manager') and hasAuthority('ASSIGN_TICKET')"
                        ))

                        .requestMatchers(
                                HttpMethod.POST,
                                "/manager/tickets/*/status",
                                "/manager/tickets/*/comments"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('Department Manager') and hasAuthority('UPDATE_TICKET')"
                        ))

                        .requestMatchers(
                                HttpMethod.GET,
                                "/manager/tickets/**"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('Department Manager') and hasAuthority('VIEW_ALL_TICKETS')"
                        ))

                        // System Administrator ticket handling
                        .requestMatchers(
                                HttpMethod.POST,
                                "/admin/tickets/*/assign"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('System Administrator') and hasAuthority('ASSIGN_TICKET')"
                        ))

                        .requestMatchers(
                                HttpMethod.POST,
                                "/admin/tickets/*/status",
                                "/admin/tickets/*/comments"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('System Administrator') and hasAuthority('UPDATE_TICKET')"
                        ))

                        .requestMatchers(
                                HttpMethod.GET,
                                "/admin/tickets/**"
                        ).access(new WebExpressionAuthorizationManager(
                                "hasRole('System Administrator') and hasAuthority('VIEW_ALL_TICKETS')"
                        ))

                        // Administration permissions
                        .requestMatchers("/admin/users/**")
                        .access(new WebExpressionAuthorizationManager(
                                "hasRole('System Administrator') and hasAuthority('MANAGE_USERS')"
                        ))

                        .requestMatchers("/admin/faqs/**")
                        .access(new WebExpressionAuthorizationManager(
                                "hasRole('System Administrator') and hasAuthority('MANAGE_FAQ')"
                        ))

                        .requestMatchers("/admin/reports/**")
                        .access(new WebExpressionAuthorizationManager(
                                "hasRole('System Administrator') and hasAuthority('VIEW_REPORTS')"
                        ))

                        .requestMatchers("/management/reports/**")
                        .access(new WebExpressionAuthorizationManager(
                                "hasRole('University Management') and hasAuthority('VIEW_REPORTS')"
                        ))

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

                        .requestMatchers("/notifications", "/notifications/**")
                        .authenticated()

                        // Shared authenticated pages such as FAQ and dashboards
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
