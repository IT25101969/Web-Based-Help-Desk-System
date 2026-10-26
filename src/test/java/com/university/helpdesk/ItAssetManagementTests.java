package com.university.helpdesk;

import com.university.helpdesk.dto.AssetForm;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest @Transactional
class ItAssetManagementTests extends TestAccounts {
    @Autowired ItAssetService service;
    @Autowired ItAssetRepository assets;
    @Autowired DepartmentRepository departments;
    @Autowired UserAccountRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TicketRepository tickets;
    @Autowired TicketService ticketService;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    Department department;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        department = new Department(); department.setDepartmentName("Assets " + UUID.randomUUID());
        department = departments.saveAndFlush(department);
    }
    AssetForm form() {
        var f = new AssetForm(); f.setAssetTag("A-" + UUID.randomUUID()); f.setAssetName("Lecture projector");
        f.setAssetType(AssetType.PROJECTOR); f.setDepartmentId(department.getDepartmentId()); f.setLocation("Lecture Hall 2");
        return f;
    }
    ItAsset create() { return service.save(null, form(), "ADM001", "127.0.0.1"); }
    Category category() {
        var c = new Category(); c.setCategoryName("Equipment " + UUID.randomUUID()); c.setDepartment(department);
        return categories.saveAndFlush(c);
    }
    @Test void createReadUpdateAndDeleteSurviveDatabaseFlush() {
        var f = form(); f.setSerialNumber("SN-" + UUID.randomUUID());
        Long id = service.save(null, f, "ADM001", "127.0.0.1").getAssetId(); em.clear();
        var stored = service.get(id); assertEquals(f.getAssetTag().toUpperCase(), stored.getAssetTag());
        var edit = AssetForm.from(stored); edit.setLocation("IT workshop"); edit.setCondition(AssetCondition.UNDER_MAINTENANCE);
        service.save(id, edit, "ADM001", "127.0.0.1"); em.clear();
        assertEquals("IT workshop", service.get(id).getLocation());
        assertEquals(AssetCondition.UNDER_MAINTENANCE, service.get(id).getCondition());
        service.delete(id, "ADM001", "127.0.0.1"); em.clear(); assertFalse(assets.existsById(id));
    }
    @Test void duplicateTagsSerialNumbersAndInvalidFieldsAreRejected() {
        var f = form(); f.setSerialNumber("SERIAL-" + UUID.randomUUID()); service.save(null, f, "ADM001", null);
        f.setAssetTag(f.getAssetTag().toLowerCase());
        assertThrows(IllegalArgumentException.class, () -> service.save(null, f, "ADM001", null));
        var other = form(); other.setSerialNumber(f.getSerialNumber().toLowerCase());
        assertThrows(IllegalArgumentException.class, () -> service.save(null, other, "ADM001", null));
        other.setSerialNumber(null); other.setAssetTag("bad tag");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, other, "ADM001", null));
        other.setAssetTag("VALID-TAG"); other.setPurchaseDate(java.time.LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.save(null, other, "ADM001", null));
    }
    @Test void staleEditsCannotOverwriteNewerAssetDetails() {
        var a = create(); var first = AssetForm.from(a); var stale = AssetForm.from(a);
        first.setLocation("New room"); service.save(a.getAssetId(), first, "ADM001", null);
        stale.setLocation("Old room");
        assertThrows(IllegalArgumentException.class, () -> service.save(a.getAssetId(), stale, "ADM001", null));
        assertEquals("New room", service.get(a.getAssetId()).getLocation());
    }
    @Test void linkedTicketsPreserveRoutingHistoryAndPreventAssetDeletion() throws Exception {
        var a = create(); var c = category();
        var ticket = ticketService.createTicketWithAsset("STU001", c.getCategoryId(), TicketType.INCIDENT,
                "Projector does not work", "No display in lecture hall", "Display", "High", null, a.getAssetId());
        Long ticketId = ticket.getTicketId(); em.flush(); em.clear();
        var stored = tickets.findById(ticketId).orElseThrow(); assertEquals(a.getAssetId(), stored.getAsset().getAssetId());
        assertEquals(department.getDepartmentId(), stored.getCategory().getDepartment().getDepartmentId());
        assertEquals(1, service.history(a.getAssetId(), 0).getTotalElements());
        assertThrows(IllegalArgumentException.class, () -> service.delete(a.getAssetId(), "ADM001", null));
        var edit = AssetForm.from(service.get(a.getAssetId())); edit.setCondition(AssetCondition.RETIRED);
        service.save(a.getAssetId(), edit, "ADM001", null); em.clear();
        assertEquals(a.getAssetId(), tickets.findById(ticketId).orElseThrow().getAsset().getAssetId());
        assertFalse(service.selectable("STU001").stream().anyMatch(o -> o.id().equals(a.getAssetId())));
        assertThrows(IllegalArgumentException.class, () -> ticketService.createTicketWithAsset("STU001", c.getCategoryId(), TicketType.INCIDENT,
                "Retired equipment", "Invalid selection", null, null, null, a.getAssetId()));
    }
    @Test void assignedEquipmentIsPrivateAndForgedTicketLinksAreBlocked() throws Exception {
        var shared = create(); var f = form(); f.setAssignedUserId(users.findByUniversityId("STU002").orElseThrow().getUserId());
        var privateAsset = service.save(null, f, "ADM001", null); var c = category();
        assertTrue(service.selectable("STU001").stream().anyMatch(o -> o.id().equals(shared.getAssetId())));
        assertFalse(service.selectable("STU001").stream().anyMatch(o -> o.id().equals(privateAsset.getAssetId())));
        assertTrue(service.selectable("STU002").stream().anyMatch(o -> o.id().equals(privateAsset.getAssetId())));
        assertThrows(AccessDeniedException.class, () -> ticketService.createTicketWithAsset("STU001", c.getCategoryId(), TicketType.INCIDENT,
                "Forged selection", "Another user's asset", null, null, null, privateAsset.getAssetId()));
    }
    @Test void adminPagesRenderAndMutationsRequireRolePermissionAndCsrf() throws Exception {
        var a = create(); var admin = user("ADM001").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_System Administrator"), new org.springframework.security.core.authority.SimpleGrantedAuthority("MANAGE_SYSTEM"));
        for (String path : new String[]{"/admin/assets", "/admin/assets/new", "/admin/assets/" + a.getAssetId(), "/admin/assets/" + a.getAssetId() + "/edit"})
            mvc.perform(get(path).with(admin)).andExpect(status().isOk()).andExpect(content().string(containsString("IT Assets")));
        mvc.perform(get("/admin/assets").with(user("STU001").roles("Student"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/assets").with(user("ADM001").roles("System Administrator"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/assets/" + a.getAssetId() + "/delete").with(admin)).andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> service.save(null, form(), "STU001", null));
        mvc.perform(get("/admin/assets/999999999").with(admin)).andExpect(status().isNotFound());
    }
    @Test void postedAssetCrudAndValidationPreserveFormInput() throws Exception {
        var admin = user("ADM001").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_System Administrator"), new org.springframework.security.core.authority.SimpleGrantedAuthority("MANAGE_SYSTEM"));
        String tag = "WEB-" + UUID.randomUUID();
        var created = mvc.perform(post("/admin/assets").with(admin).with(csrf()).param("assetTag", tag)
                .param("assetName", "Browser printer").param("assetType", "PRINTER").param("departmentId", department.getDepartmentId().toString())
                .param("location", "Library").param("condition", "WORKING")).andExpect(status().is3xxRedirection()).andReturn();
        Long id = Long.valueOf(created.getResponse().getRedirectedUrl().substring("/admin/assets/".length()));
        mvc.perform(post("/admin/assets/" + id).with(admin).with(csrf()).param("version", service.get(id).getVersion().toString())
                .param("assetTag", tag).param("assetName", "Renamed printer").param("assetType", "PRINTER")
                .param("departmentId", department.getDepartmentId().toString()).param("location", "Room 4").param("condition", "WORKING"))
                .andExpect(redirectedUrl("/admin/assets/" + id));
        assertEquals("Renamed printer", service.get(id).getAssetName());
        mvc.perform(post("/admin/assets").with(admin).with(csrf()).param("assetName", "Keep my input"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Keep my input")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andExpect(content().string(containsString("id=\"assetTag-error\"")));
        mvc.perform(post("/admin/assets").with(admin).with(csrf()).param("assetTag", "VALID-FORM-TAG")
                .param("assetName", "   ").param("assetType", "PRINTER")
                .param("departmentId", department.getDepartmentId().toString()).param("location", "Library")
                .param("purchaseDate", java.time.LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter an asset name.")))
                .andExpect(content().string(containsString("Purchase date cannot be in the future.")));
        mvc.perform(post("/admin/assets/" + id + "/delete").with(admin).with(csrf())).andExpect(redirectedUrl("/admin/assets"));
        assertFalse(assets.existsById(id));
    }
    @Test void filtersAndPaginationSelectOnlyMatchingAssets() {
        String marker = "SEARCH-" + UUID.randomUUID();
        for (int i = 0; i < 13; i++) { var f = form(); f.setAssetName(marker); service.save(null, f, "ADM001", null); }
        create();
        var page = service.search(marker, AssetType.PROJECTOR, AssetCondition.WORKING, department.getDepartmentId(), 0);
        assertEquals(13, page.getTotalElements()); assertEquals(12, page.getNumberOfElements());
        assertEquals(1, service.search(marker, null, null, null, 99).getNumberOfElements());
        assertEquals(0, service.search(marker, AssetType.PRINTER, null, null, 0).getTotalElements());
        assertEquals(0, service.search("%", null, null, null, 0).getTotalElements());
    }
    @Test void supportMemberSeesOnlyTheirAssignedAssetsAndReassignmentsRefreshTheView() throws Exception {
        var f = form(); f.setAssignedUserId(users.findByUniversityId("SUP001").orElseThrow().getUserId());
        var assigned = service.save(null, f, "ADM001", null);
        var shared = create();
        var privateForm = form(); privateForm.setAssignedUserId(users.findByUniversityId("STU002").orElseThrow().getUserId());
        var other = service.save(null, privateForm, "ADM001", null);
        em.flush(); em.clear();
        var staff = user("SUP001").roles("Help Desk Support Staff");
        mvc.perform(get("/my-assets").with(staff).param("universityId", "STU002"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(assigned.getAssetTag())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(shared.getAssetTag()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(other.getAssetTag()))))
                .andExpect(content().string(containsString("My Assets")));
        mvc.perform(get("/staff/dashboard").with(staff)).andExpect(status().isOk())
                .andExpect(content().string(containsString("View Assigned Equipment")));
        mvc.perform(get("/admin/assets/" + assigned.getAssetId() + "/edit").with(staff))
                .andExpect(status().isForbidden());
        var edit = AssetForm.from(service.get(assigned.getAssetId()));
        edit.setAssignedUserId(users.findByUniversityId("STU002").orElseThrow().getUserId());
        service.save(assigned.getAssetId(), edit, "ADM001", null); em.clear();
        mvc.perform(get("/my-assets").with(staff)).andExpect(status().isOk())
                .andExpect(content().string(containsString("No assigned equipment")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(assigned.getAssetTag()))));
        assertTrue(service.assignedTo("STU002", 0).stream().anyMatch(a -> a.getAssetId().equals(assigned.getAssetId())));
    }
    @Test void assignedAssetsIncludeRetiredEquipmentAndRequireAuthentication() throws Exception {
        var f = form(); f.setAssignedUserId(users.findByUniversityId("SUP001").orElseThrow().getUserId());
        f.setCondition(AssetCondition.RETIRED);
        var assigned = service.save(null, f, "ADM001", null); em.clear();
        mvc.perform(get("/my-assets")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/my-assets").with(user("SUP001").roles("Help Desk Support Staff")).param("page", "999"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(assigned.getAssetTag())))
                .andExpect(content().string(containsString("Retired")));
        assertThrows(AccessDeniedException.class, () -> service.assignedTo("UNKNOWN-ACCOUNT", 0));
    }
    @Test void studentSubmissionAndTicketPagesRenderLinkedEquipment() throws Exception {
        var a = create(); var c = category();
        var student = user("STU001").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_Student"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SUBMIT_TICKET"), new org.springframework.security.core.authority.SimpleGrantedAuthority("VIEW_OWN_TICKETS"));
        mvc.perform(get("/student/tickets/new").with(student)).andExpect(status().isOk()).andExpect(content().string(containsString(a.getAssetTag())));
        var result = mvc.perform(multipart("/student/tickets").with(student).with(csrf()).param("categoryId", c.getCategoryId().toString())
                .param("ticketType", "INCIDENT").param("subject", "Broken projector").param("description", "No image")
                .param("assetId", a.getAssetId().toString())).andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get(result.getResponse().getRedirectedUrl()).with(student)).andExpect(status().isOk()).andExpect(content().string(containsString(a.getAssetTag())));
        Long id = Long.valueOf(result.getResponse().getRedirectedUrl().replace("/student/tickets/", ""));
        mvc.perform(get("/admin/tickets/" + id).with(user("ADM001").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_System Administrator"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("VIEW_ALL_TICKETS"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("MANAGE_SYSTEM"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("View asset and support history")));
    }
    @Autowired AssetMaintenanceReportRepository repairs;
    @Autowired NotificationRepository notifications;
    @Test void maintenanceSubmissionReviewAndResubmissionPersistAndNotify() throws Exception {
        var f = form(); f.setAssignedUserId(users.findByUniversityId("SUP001").orElseThrow().getUserId());
        f.setCondition(AssetCondition.UNDER_MAINTENANCE);
        var a = service.save(null, f, "ADM001", null);
        var staff = user("SUP001").roles("Help Desk Support Staff");
        var admin = user("ADM001").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_System Administrator"), new org.springframework.security.core.authority.SimpleGrantedAuthority("MANAGE_SYSTEM"));
        mvc.perform(post("/my-assets/" + a.getAssetId() + "/maintenance-complete").with(staff).with(csrf()).param("notes", "Replaced battery and verified charging"))
                .andExpect(redirectedUrl("/my-assets"));
        em.flush(); em.clear();
        var report = service.repairHistory(a.getAssetId()).getFirst();
        assertEquals(AssetMaintenanceReport.Status.PENDING, report.getStatus());
        assertEquals(AssetCondition.UNDER_MAINTENANCE, service.get(a.getAssetId()).getCondition());
        assertTrue(notifications.findByUserUserIdOrderByCreatedDateDesc(users.findByUniversityId("ADM001").orElseThrow().getUserId()).stream()
                .anyMatch(n -> ("/admin/assets/" + a.getAssetId()).equals(n.getActionUrl())));
        assertThrows(IllegalArgumentException.class, () -> service.completeMaintenance(a.getAssetId(), "SUP001", "Duplicate", null));
        mvc.perform(get("/admin/assets/" + a.getAssetId()).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Replaced battery")));
        mvc.perform(get("/notifications").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("View equipment update")));
        mvc.perform(post("/admin/assets/" + a.getAssetId() + "/repairs/" + report.getReportId() + "/review").with(admin).with(csrf())
                .param("decision", "return").param("notes", "Please test battery under load"))
                .andExpect(redirectedUrl("/admin/assets/" + a.getAssetId()));
        em.clear(); assertEquals(AssetMaintenanceReport.Status.MORE_WORK_REQUIRED, repairs.findById(report.getReportId()).orElseThrow().getStatus());
        mvc.perform(get("/my-assets").with(staff)).andExpect(status().isOk()).andExpect(content().string(containsString("Please test battery under load")));
        service.completeMaintenance(a.getAssetId(), "SUP001", "Load test passed", null);
        var resubmitted = service.repairHistory(a.getAssetId()).getFirst();
        service.reviewMaintenance(a.getAssetId(), resubmitted.getReportId(), "ADM001", true, "Verified", null);
        em.clear(); assertEquals(AssetCondition.WORKING, service.get(a.getAssetId()).getCondition());
        assertEquals(AssetMaintenanceReport.Status.APPROVED, repairs.findById(resubmitted.getReportId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.reviewMaintenance(a.getAssetId(), resubmitted.getReportId(), "ADM001", true, "Again", null));
        assertThrows(IllegalArgumentException.class, () -> service.delete(a.getAssetId(), "ADM001", null));
    }
    @Test void maintenanceRejectsUnauthorizedInvalidAndForgedRequests() throws Exception {
        var f = form(); f.setAssignedUserId(users.findByUniversityId("SUP001").orElseThrow().getUserId());
        f.setCondition(AssetCondition.UNDER_MAINTENANCE); var a = service.save(null, f, "ADM001", null);
        assertThrows(AccessDeniedException.class, () -> service.completeMaintenance(a.getAssetId(), "STU001", "Repair", null));
        var shared = create();
        assertThrows(AccessDeniedException.class, () -> service.completeMaintenance(shared.getAssetId(), "SUP001", "Repair", null));
        assertThrows(IllegalArgumentException.class, () -> service.completeMaintenance(a.getAssetId(), "SUP001", "   ", null));
        assertThrows(IllegalArgumentException.class, () -> service.completeMaintenance(a.getAssetId(), "SUP001", "x".repeat(2001), null));
        mvc.perform(post("/my-assets/" + a.getAssetId() + "/maintenance-complete").with(user("SUP001").roles("Help Desk Support Staff")).param("notes", "Repair"))
                .andExpect(status().isForbidden());
        service.completeMaintenance(a.getAssetId(), "SUP001", "Repaired", null);
        var r = service.repairHistory(a.getAssetId()).getFirst();
        assertThrows(AccessDeniedException.class, () -> service.reviewMaintenance(a.getAssetId(), r.getReportId(), "SUP001", true, "Okay", null));
        assertThrows(IllegalArgumentException.class, () -> service.reviewMaintenance(shared.getAssetId(), r.getReportId(), "ADM001", true, "Okay", null));
        assertThrows(IllegalArgumentException.class, () -> service.reviewMaintenance(a.getAssetId(), r.getReportId(), "ADM001", true, " ", null));
    }

}
