package com.university.helpdesk;

import com.university.helpdesk.dto.AssetForm;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real MySQL persistence; all generated records roll back. */
@EnabledIfEnvironmentVariable(named = "MYSQL_ASSET_REVIEW", matches = "true")
@SpringBootTest(properties = {"spring.datasource.url=${DB_URL}", "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "app.seed.demo=false", "app.mail.enabled=false",
        "app.mail.scheduler.enabled=false", "app.sla.alerts.enabled=false"})
@Transactional
class ItAssetMySqlTests {
    @Autowired ItAssetService service;
    @Autowired ItAssetRepository assets;
    @Autowired DepartmentRepository departments;
    @Autowired CategoryRepository categories;
    @Autowired TicketRepository tickets;
    @Autowired TicketService ticketService;
    @Autowired jakarta.persistence.EntityManager em;
    AssetForm form() {
        var d = new Department(); d.setDepartmentName("Asset SQL verification " + java.util.UUID.randomUUID());
        d = departments.saveAndFlush(d);
        var f = new AssetForm(); f.setAssetTag("SQL-" + java.util.UUID.randomUUID()); f.setAssetName("SQL verification equipment");
        f.setAssetType(AssetType.PRINTER); f.setDepartmentId(d.getDepartmentId()); f.setLocation("Verification room");
        return f;
    }
    @Test void assetCreateReadUpdateDeletePersistInMysql() {
        var a = service.save(null, form(), "ADM001", "127.0.0.1"); Long id = a.getAssetId(); em.flush(); em.clear();
        var edit = AssetForm.from(service.get(id)); edit.setLocation("Updated verification room");
        edit.setCondition(AssetCondition.UNDER_MAINTENANCE); service.save(id, edit, "ADM001", null); em.clear();
        assertEquals("Updated verification room", service.get(id).getLocation());
        assertEquals(AssetCondition.UNDER_MAINTENANCE, service.get(id).getCondition());
        service.delete(id, "ADM001", null); em.flush(); em.clear(); assertFalse(assets.existsById(id));
    }
    @Test void mysqlTicketAssetLinkAndRetirementKeepSupportHistory() throws Exception {
        var f = form(); var a = service.save(null, f, "ADM001", null);
        var c = new Category(); c.setCategoryName("Asset verification " + java.util.UUID.randomUUID());
        c.setDepartment(departments.findById(f.getDepartmentId()).orElseThrow()); c = categories.saveAndFlush(c);
        var ticket = ticketService.createTicketWithAsset("STU001", c.getCategoryId(), TicketType.INCIDENT,
                "Asset SQL verification", "Rollback-only asset workflow verification", null, null, null, a.getAssetId());
        Long ticketId = ticket.getTicketId(); em.flush(); em.clear();
        assertEquals(a.getAssetId(), tickets.findById(ticketId).orElseThrow().getAsset().getAssetId());
        var edit = AssetForm.from(service.get(a.getAssetId())); edit.setCondition(AssetCondition.RETIRED);
        service.save(a.getAssetId(), edit, "ADM001", null); em.flush(); em.clear();
        assertEquals(1, service.history(a.getAssetId(), 0).getTotalElements());
        assertEquals(AssetCondition.RETIRED, tickets.findById(ticketId).orElseThrow().getAsset().getCondition());
    }
    @Autowired UserAccountRepository users;
    @Autowired AssetMaintenanceReportRepository repairs;
    @Autowired NotificationRepository notifications;
    @Test void mysqlMaintenanceSubmissionAndApprovalPersist() {
        var f = form(); f.setAssignedUserId(users.findByUniversityId("SUP001").orElseThrow().getUserId());
        f.setCondition(AssetCondition.UNDER_MAINTENANCE);
        var a = service.save(null, f, "ADM001", null); var id = a.getAssetId();
        service.completeMaintenance(id, "SUP001", "Rollback-only repair verification", null);
        em.flush(); em.clear();
        var r = service.repairHistory(id).getFirst();
        assertEquals(AssetMaintenanceReport.Status.PENDING, r.getStatus());
        assertEquals("SUP001", r.getSubmittedBy());
        assertEquals(AssetCondition.UNDER_MAINTENANCE, service.get(id).getCondition());
        assertTrue(notifications.findByUserUserIdOrderByCreatedDateDesc(users.findByUniversityId("ADM001").orElseThrow().getUserId()).stream()
                .anyMatch(n -> ("/admin/assets/" + id).equals(n.getActionUrl())));
        service.reviewMaintenance(id, r.getReportId(), "ADM001", true, "Repair verified", null);
        em.flush(); em.clear();
        assertEquals(AssetCondition.WORKING, service.get(id).getCondition());
        assertEquals(AssetMaintenanceReport.Status.APPROVED, repairs.findById(r.getReportId()).orElseThrow().getStatus());
        assertEquals("Repair verified", repairs.findById(r.getReportId()).orElseThrow().getReviewNotes());
    }

}
