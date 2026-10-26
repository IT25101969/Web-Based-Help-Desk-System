package com.university.helpdesk;

import com.university.helpdesk.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, cookies, CSRF, BCrypt login, database authorities and server-rendered pages. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:helpdesk-live-http;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LiveHttpSmokeTests extends TestAccounts {
    @Value("${local.server.port}") int port;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired com.university.helpdesk.service.PasswordResetService resets;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.university.helpdesk.service.AdminAccountService accountAccess;
    @Test void disablingAccountExpiresSessionAfterCommitAndRetainsItAfterRollback() throws Exception {
        String password=UUID.randomUUID().toString();
        var account=users.findByUniversityId("STU001").orElseThrow();
        account.setPasswordHash(encoder.encode(password)); users.saveAndFlush(account);
        try (var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build()) {
            assertEquals(302,post(client,"/login",Map.of("login","STU001","password",password,"_csrf",csrf(get(client,"/login").body()))).statusCode());
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->{
                accountAccess.status(account.getUserId(),com.university.helpdesk.entity.AccountStatus.DISABLED,"ADM001",null);
                tx.setRollbackOnly();
            });
            assertEquals(200,get(client,"/faq").statusCode());
            accountAccess.status(account.getUserId(),com.university.helpdesk.entity.AccountStatus.DISABLED,"ADM001",null);
            var expired=get(client,"/faq");
            assertEquals(302,expired.statusCode());
            assertTrue(expired.headers().firstValue("location").orElseThrow().contains("/login?expired"));
        } finally {
            accountAccess.status(account.getUserId(),com.university.helpdesk.entity.AccountStatus.ACTIVE,"ADM001",null);
        }
    }

    HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(HttpClient client, String path, Map<String, String> values) throws Exception {
        String body = values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), UTF_8) + "=" + URLEncoder.encode(e.getValue(), UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "Missing CSRF field");
        return matcher.group(1);
    }
    @Test void passwordResetExpiresExistingSessionsOnlyAfterCommit() throws Exception {
        String original = UUID.randomUUID().toString();
        String replacement = UUID.randomUUID().toString();
        var account = users.findByUniversityId("STU001").orElseThrow();
        account.setPasswordHash(encoder.encode(original));
        users.saveAndFlush(account);
        try (var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()) {
            assertEquals(302, post(client, "/login", Map.of("login", "STU001", "password", original,
                    "_csrf", csrf(get(client, "/login").body()))).statusCode());
            assertEquals(200, get(client, "/student/dashboard").statusCode());
            String token = resets.createResetToken("STU001", "127.0.0.1").orElseThrow();
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertTrue(resets.resetPassword(token, replacement, "127.0.0.1"));
                status.setRollbackOnly();
            });
            assertEquals(200, get(client, "/student/dashboard").statusCode(), "A rollback must retain the session");
            assertTrue(resets.resetPassword(token, replacement, "127.0.0.1"));
            assertEquals(302, get(client, "/student/dashboard").statusCode(), "Old sessions must expire after reset");
            var rejected = post(client, "/login", Map.of("login", "STU001", "password", original,
                    "_csrf", csrf(get(client, "/login").body())));
            assertTrue(rejected.headers().firstValue("location").orElseThrow().contains("error"));
            assertEquals(302, post(client, "/login", Map.of("login", "STU001", "password", replacement,
                    "_csrf", csrf(get(client, "/login").body()))).statusCode());
            assertEquals(200, get(client, "/student/dashboard").statusCode());
        }
    }
    @Test void anonymousPublicErrorsKeepTheirHttpStatusWithoutRedirectingToLogin() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var invalidSearch = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/help?categoryId=invalid"))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalidSearch.statusCode());
            assertTrue(invalidSearch.body().contains("</html>"));
            assertTrue(invalidSearch.body().contains("Return to Home"));
            assertFalse(invalidSearch.body().contains("SIGNED IN AS"));
            assertTrue(invalidSearch.headers().firstValue("location").isEmpty());
            assertEquals(404, get(client, "/images/nonexistent-verification-image.svg").statusCode());
            assertEquals(302, get(client, "/admin/users").statusCode());
            assertEquals(302, get(client, "/student/dashboard").statusCode());
        }
    }
    @Test void realLoginAndEveryRoleMenuWorkWithDatabasePermissions() throws Exception {
        try (var anonymous = HttpClient.newHttpClient()) {
            for (String path : List.of("/", "/help", "/privacy", "/login", "/forgot-password", "/reset-password", "/css/app.css", "/css/public.css", "/js/ticket-draft.js")) {
                assertEquals(200, get(anonymous, path).statusCode(), path);
            }
        }
        var destinations = new LinkedHashMap<String, List<String>>();
        destinations.put("STU001", List.of("/student/dashboard", "/student/tickets", "/student/tickets/new"));
        destinations.put("SUP001", List.of("/staff/dashboard", "/staff/tickets"));
        destinations.put("DSU001", List.of("/staff/dashboard", "/staff/tickets"));
        destinations.put("MGR001", List.of("/manager/dashboard", "/manager/tickets"));
        destinations.put("ADM001", List.of("/admin/dashboard", "/admin/tickets", "/admin/users", "/admin/service-setup", "/admin/assets", "/admin/assets/new", "/admin/support-tools", "/admin/faqs", "/admin/faqs/search-analytics", "/admin/reports", "/admin/monitoring", "/admin/activity-logs"));
        destinations.put("UMG001", List.of("/management/dashboard", "/management/reports"));
        for (var entry : destinations.entrySet()) {
            // Random temporary passwords stay in memory and are never logged.
            String password = UUID.randomUUID().toString();
            var account = users.findByUniversityId(entry.getKey()).orElseThrow();
            account.setPasswordHash(encoder.encode(password)); users.saveAndFlush(account);
            try (var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()) {
                String loginForm = get(client, "/login").body();
                var login = post(client, "/login", Map.of("login", entry.getKey(), "password", password, "_csrf", csrf(loginForm)));
                assertEquals(302, login.statusCode(), entry.getKey() + " login");
                assertEquals(entry.getValue().getFirst(), URI.create(login.headers().firstValue("location").orElseThrow()).getPath());
                var paths = new ArrayList<>(entry.getValue());
                paths.addAll(List.of("/faq", "/notifications", "/notifications/preferences"));
                String html = "";
                for (String path : paths) {
                    var response = get(client, path);
                    assertEquals(200, response.statusCode(), entry.getKey() + " " + path);
                    html = response.body();
                    assertTrue(html.contains("</html>"), path + " did not render completely");
                }
                if (entry.getKey().equals("STU001")) assertEquals(403, get(client, "/admin/users").statusCode());
                assertEquals(302, post(client, "/logout", Map.of("_csrf", csrf(html))).statusCode());
                assertEquals(302, get(client, entry.getValue().getFirst()).statusCode());
            }
        }
    }
}
