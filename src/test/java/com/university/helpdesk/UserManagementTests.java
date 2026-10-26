package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.UserProfileService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest @Transactional
class UserManagementTests extends TestAccounts {
    @Autowired UserProfileService profiles;
    @Autowired UserAccountRepository users;
    @Autowired UserRoleRepository roles;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("ADM001").authorities(() -> "ROLE_System Administrator", () -> "MANAGE_USERS");
    }
    @Test void profileChangesPersistWithoutChangingPasswordRolesOrStatus() {
        var student = users.findByUniversityId("STU001").orElseThrow();
        Long id = student.getUserId(); String password = student.getPasswordHash();
        int roleCount = roles.findByUserUserIdAndActiveTrue(id).size();
        profiles.update(id, " STU_UPDATED ", " updated@fixtures.invalid ", " Updated ", " Student ", "ADM001", "127.0.0.1");
        entityManager.flush(); entityManager.clear();
        var stored = users.findById(id).orElseThrow();
        assertEquals("STU_UPDATED", stored.getUniversityId()); assertEquals("updated@fixtures.invalid", stored.getEmail());
        assertEquals("Updated", stored.getFirstName()); assertEquals("Student", stored.getLastName());
        assertEquals(password, stored.getPasswordHash()); assertEquals(AccountStatus.ACTIVE, stored.getAccountStatus());
        assertEquals(roleCount, roles.findByUserUserIdAndActiveTrue(id).size());
    }
    @Test void duplicateAndInvalidFieldsAreRejectedBeforeModifyingProfile() {
        var target = users.findByUniversityId("STU001").orElseThrow();
        var other = users.findByUniversityId("STU002").orElseThrow();
        Long id = target.getUserId(); String originalEmail = target.getEmail();
        assertThrows(IllegalArgumentException.class, () -> profiles.update(id, "stu002", originalEmail, "Changed", "Name", "ADM001", null));
        assertThrows(IllegalArgumentException.class, () -> profiles.update(id, "STU001", other.getEmail(), "Changed", "Name", "ADM001", null));
        assertThrows(IllegalArgumentException.class, () -> profiles.update(id, "STU001", "not-an-email", "Changed", "Name", "ADM001", null));
        assertThrows(IllegalArgumentException.class, () -> profiles.update(id, "STU001", originalEmail, " ", "Name", "ADM001", null));
        assertThrows(IllegalArgumentException.class, () -> profiles.update(id, "x".repeat(51), originalEmail, "Changed", "Name", "ADM001", null));
        assertEquals("STU001", target.getUniversityId()); assertEquals(originalEmail, target.getEmail());
    }
    @Test void profileEditorRendersAndPostsWithAdminPermissions() throws Exception {
        Long id = users.findByUniversityId("STU001").orElseThrow().getUserId();
        mvc.perform(get("/admin/users/" + id + "/edit").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Profile details"))).andExpect(content().string(containsString("Access &amp; permissions")));
        mvc.perform(post("/admin/users/" + id + "/profile").with(admin()).with(csrf())
                .param("universityId", "STU001").param("email", "profile@fixtures.invalid").param("firstName", "Profile").param("lastName", "Edited"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        entityManager.flush(); entityManager.clear(); assertEquals("Profile", users.findById(id).orElseThrow().getFirstName());
    }
    @Test void profileMutationsRequireCsrfAndManageUsersPermission() throws Exception {
        Long id = users.findByUniversityId("STU001").orElseThrow().getUserId();
        mvc.perform(post("/admin/users/" + id + "/profile").with(admin())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users/" + id + "/edit").with(user("ADM001").roles("System Administrator"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/" + id + "/profile").with(csrf()).with(user("STU001").roles("Student"))).andExpect(status().isForbidden());
    }
    @Test void invalidEditPreservesEnteredNonSecretFields() throws Exception {
        Long id = users.findByUniversityId("STU001").orElseThrow().getUserId();
        mvc.perform(post("/admin/users/" + id + "/profile").with(admin()).with(csrf())
                .param("universityId", "STU001").param("email", "bad").param("firstName", "Keep me").param("lastName", "Student"))
                .andExpect(flash().attributeExists("error", "profileValues"));
    }
    @Test void directorySearchFiltersAndPaginationRender() throws Exception {
        mvc.perform(get("/admin/users").with(admin()).param("q", "STU001").param("status", "ACTIVE").param("page", "999"))
                .andExpect(status().isOk()).andExpect(model().attribute("resultCount", 1)).andExpect(model().attribute("page", 0))
                .andExpect(content().string(containsString("Manage account for STU001")));
        mvc.perform(get("/admin/users").with(admin()).param("q", "no-such-person-fixture"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No accounts found")));
        mvc.perform(get("/admin/users/9223372036854775807/edit").with(admin())).andExpect(status().isNotFound());
    }
    @Test void changingIdentityInvalidatesOutstandingResetLinks() {
        var student = users.findByUniversityId("STU001").orElseThrow();
        var token = new PasswordResetToken(); token.setUser(student);
        token.setTokenHash(java.util.UUID.randomUUID().toString()); token.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(30));
        tokens.saveAndFlush(token); Long tokenId = token.getTokenId();
        profiles.update(student.getUserId(), "STU001", "changed@fixtures.invalid", "New", "Name", "ADM001", null);
        entityManager.flush(); entityManager.clear();
        assertTrue(tokens.findById(tokenId).orElseThrow().getUsed());
    }
    @Test void accessActionsReturnToEditorWhenRequested() throws Exception {
        Long id = users.findByUniversityId("STU001").orElseThrow().getUserId();
        mvc.perform(post("/admin/users/" + id + "/status").with(admin()).with(csrf())
                .param("status", "ACTIVE").param("returnTo", "profile"))
                .andExpect(redirectedUrl("/admin/users/" + id + "/edit"));
    }
    @Test void activeAccountCannotLoseItsLastRole() throws Exception {
        Long id = users.findByUniversityId("STU001").orElseThrow().getUserId();
        var assigned = roles.findByUserUserIdAndActiveTrue(id);
        assertEquals(1, assigned.size());
        mvc.perform(post("/admin/users/" + id + "/roles/" + assigned.get(0).getRole().getRoleId() + "/remove")
                .with(admin()).with(csrf())).andExpect(flash().attributeExists("error"));
        entityManager.flush(); entityManager.clear();
        assertEquals(1, roles.findByUserUserIdAndActiveTrue(id).size());
    }
    @Test void rolelessAccountCannotBeActivatedOrUnlocked() throws Exception {
        var account = users.findByUniversityId("STU001").orElseThrow();
        Long id = account.getUserId();
        account.setAccountStatus(AccountStatus.DISABLED);
        for (var role : roles.findByUserUserIdAndActiveTrue(id)) role.setActive(false);
        entityManager.flush(); entityManager.clear();
        mvc.perform(post("/admin/users/" + id + "/status").with(admin()).with(csrf()).param("status", "ACTIVE"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/users/" + id + "/unlock").with(admin()).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        entityManager.flush(); entityManager.clear();
        assertEquals(AccountStatus.DISABLED, users.findById(id).orElseThrow().getAccountStatus());
    }
    @Test void renderOptionalVisualPreview() throws Exception {
        String previewDir = System.getProperty("ui.preview.dir");
        if (previewDir == null) return;
        var directory = java.nio.file.Path.of(previewDir); java.nio.file.Files.createDirectories(directory);
        var listing = mvc.perform(get("/admin/users").with(admin())).andExpect(status().isOk()).andReturn();
        java.nio.file.Files.writeString(directory.resolve("users.html"), listing.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        Long id = users.findByUniversityId("STU001").orElseThrow().getUserId();
        var editor = mvc.perform(get("/admin/users/" + id + "/edit").with(admin())).andExpect(status().isOk()).andReturn();
        java.nio.file.Files.writeString(directory.resolve("edit.html"), editor.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
