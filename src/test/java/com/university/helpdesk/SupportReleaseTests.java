package com.university.helpdesk;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @Transactional
class SupportReleaseTests extends TestAccounts {
    @Autowired WebApplicationContext context;
    @Autowired SupportToolsService tools;
    @Autowired CampusAnnouncementRepository announcements;
    @Autowired SavedReplyRepository replies;
    @Autowired TicketDraftRepository drafts;
    @Autowired UserAccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired DepartmentRepository departments;
    @Autowired UserDepartmentRepository memberships;
    @Autowired TicketAssignmentRepository assignments;
    @Autowired TicketRepository tickets;
    @Autowired TicketService ticketService;
    @Autowired OverdueAlertService alerts;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationPreferenceService preferences;
    @Autowired EmailNotificationQueueRepository emails;
    @Autowired NotificationService notificationService;
    @Autowired UserRoleRepository userRoles;
    MockMvc mvc;
    Notification notice(String owner, NotificationType type, boolean read, LocalDateTime time) {
        Notification n=new Notification();n.setUser(accounts.findByUniversityId(owner).orElseThrow());n.setNotificationType(type);n.setMessage("Inbox verification");n.setReadStatus(read);org.springframework.test.util.ReflectionTestUtils.setField(n,"createdDate",time);return notifications.saveAndFlush(n);
    }
    @Test void notificationFiltersAndPaginationStayWithinTheOwnersInbox() throws Exception {
        LocalDateTime time=LocalDateTime.now();
        for(int i=0;i<12;i++)notice("STU001",NotificationType.OVERDUE,false,time);
        notice("STU001",NotificationType.SYSTEM,false,time);
        notice("STU001",NotificationType.OVERDUE,true,time);
        notice("STU002",NotificationType.OVERDUE,false,time);
        Long owner=accounts.findByUniversityId("STU001").orElseThrow().getUserId();
        var first=notificationService.searchForUser(owner,true,NotificationType.OVERDUE,0,5);
        var second=notificationService.searchForUser(owner,true,NotificationType.OVERDUE,1,5);
        assertEquals(12,first.getTotalElements());assertEquals(5,first.getNumberOfElements());
        assertTrue(first.getContent().stream().noneMatch(second.getContent()::contains));
        assertTrue(first.getContent().get(0).getNotificationId()>first.getContent().get(1).getNotificationId());
        mvc.perform(get("/notifications").param("filter","unread").param("type","OVERDUE").param("size","5").with(user("STU001").roles("Student")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("12 matching updates"))).andExpect(content().string(containsString("Page 1 of 3")));
    }
    @Test void notificationPaginationBoundsAndEmptyResultsAreSafe(){
        Long owner=accounts.findByUniversityId("STU001").orElseThrow().getUserId();
        notice("STU001",NotificationType.SYSTEM,false,LocalDateTime.now());
        var page=notificationService.searchForUser(owner,false,null,Integer.MAX_VALUE,5000);
        assertEquals(0,page.getNumber());assertEquals(50,page.getSize());assertEquals(1,page.getNumberOfElements());
        var empty=notificationService.searchForUser(owner,true,NotificationType.CLOSED,-5,-5);
        assertEquals(0,empty.getNumber());assertEquals(5,empty.getSize());assertTrue(empty.isEmpty());
    }
    @Test void formerStaffDoNotReceiveOverdueTicketDetails(){
        Department d=new Department();d.setDepartmentName(UUID.randomUUID().toString());departments.save(d);member("SUP001",d);member("MGR001",d);
        Ticket t=ticket(d);t.setCreatedDate(LocalDateTime.now().minusDays(10));tickets.saveAndFlush(t);
        var staff=accounts.findByUniversityId("SUP001").orElseThrow();
        TicketAssignment a=new TicketAssignment();a.setTicket(t);a.setAssignedToUser(staff);a.setAssignedByUser(accounts.findByUniversityId("ADM001").orElseThrow());assignments.saveAndFlush(a);
        for(var role:userRoles.findByUserUserIdAndActiveTrue(staff.getUserId())){role.setActive(false);userRoles.saveAndFlush(role);}
        assertEquals(1,alerts.alert(t.getTicketId(),LocalDateTime.now()));
        assertTrue(notifications.findByUserUserIdOrderByCreatedDateDesc(staff.getUserId()).stream().noneMatch(n->n.getNotificationType()==NotificationType.OVERDUE));
    }
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    Ticket ticket(Department department){
        Category c=new Category();c.setCategoryName(UUID.randomUUID().toString());c.setDepartment(department);categories.save(c);
        return ticketService.createTicket("STU001",c.getCategoryId(),TicketType.INCIDENT,"Release verification","Request details","Connectivity","CRITICAL");
    }
    void member(String id,Department d){UserDepartment m=new UserDepartment();m.setUser(accounts.findByUniversityId(id).orElseThrow());m.setDepartment(d);memberships.save(m);}
    @Test void announcementsExposeOnlyPublishedCurrentNotices() throws Exception {
        String marker=UUID.randomUUID().toString();LocalDateTime now=LocalDateTime.now();
        tools.saveAnnouncement(null,marker+" live","Visible update","OUTAGE",true,now.minusHours(1),now.plusHours(1),"ADM001","127.0.0.1");
        tools.saveAnnouncement(null,marker+" draft","Not public","INFORMATION",false,now.minusHours(1),null,"ADM001","127.0.0.1");
        tools.saveAnnouncement(null,marker+" future","Scheduled","MAINTENANCE",true,now.plusDays(1),null,"ADM001","127.0.0.1");
        tools.saveAnnouncement(null,marker+" expired","Past","OUTAGE",true,now.minusDays(2),now.minusDays(1),"ADM001","127.0.0.1");
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString(marker+" live")))
                .andExpect(content().string(not(containsString(marker+" draft")))).andExpect(content().string(not(containsString(marker+" future"))))
                .andExpect(content().string(not(containsString(marker+" expired"))));
    }
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ActivityLogRepository activityLogs;
    @Test void announcementDeletionRemovesPublishedNoticeAndKeepsAuditRecord() throws Exception {
        String title = "Delete verification " + UUID.randomUUID();
        var notice = tools.saveAnnouncement(null, title, "Public notice", "INFORMATION", true, LocalDateTime.now().minusMinutes(1), null, "ADM001", null);
        Long id = notice.getId();
        var admin = user("ADM001").authorities(() -> "ROLE_System Administrator", () -> "MANAGE_SYSTEM");
        mvc.perform(get("/admin/support-tools").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/support-tools/announcements/" + id + "/delete")))
                .andExpect(content().string(containsString("data-confirm=")));
        mvc.perform(post("/admin/support-tools/announcements/" + id + "/delete").with(admin))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/support-tools/announcements/" + id + "/delete").with(user("SUP001").roles("Help Desk Support Staff")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/support-tools/announcements/" + id + "/delete").with(user("ADM001").roles("System Administrator")).with(csrf()))
                .andExpect(status().isForbidden());
        assertTrue(announcements.existsById(id));
        mvc.perform(post("/admin/support-tools/announcements/" + id + "/delete").with(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/support-tools#announcements"))
                .andExpect(flash().attributeExists("success"));
        entityManager.flush(); entityManager.clear();
        assertFalse(announcements.existsById(id));
        assertTrue(activityLogs.findAll().stream().anyMatch(l -> "ANNOUNCEMENT_DELETED".equals(l.getAction()) && id.equals(l.getEntityId())));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(not(containsString(title))));
        mvc.perform(post("/admin/support-tools/announcements/" + id + "/delete").with(admin).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> tools.deleteAnnouncement(id, "SUP001", null));
    }
    @Test void draftAnnouncementCanBeDeleted() {
        var a = tools.saveAnnouncement(null, "Draft verification", "Draft notice", "INFORMATION", false, null, null, "ADM001", null);
        tools.deleteAnnouncement(a.getId(), "ADM001", null); entityManager.clear();
        assertFalse(announcements.existsById(a.getId()));
    }
    @Test void toolsRejectInvalidSchedulesAndRequireSystemPermission() throws Exception {
        LocalDateTime now=LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,()->tools.saveAnnouncement(null,"Notice","Details","OUTAGE",true,now,now.minusMinutes(1),"ADM001","127.0.0.1"));
        mvc.perform(post("/admin/support-tools/replies").param("title","Unauthorized").param("message","Not allowed").with(csrf()).with(user("ADM001").roles("System Administrator"))).andExpect(status().isForbidden());
    }
    @Test void draftsArePartialPrivateAndRequireCsrfAndSubmissionPermission() throws Exception {
        var student=user("STU001").authorities(()->"ROLE_Student",()->"SUBMIT_TICKET");
        mvc.perform(post("/student/draft").contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"My private draft\"}").with(student).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.subject").value("My private draft"));
        mvc.perform(get("/student/draft").with(user("STU002").authorities(()->"ROLE_Student",()->"SUBMIT_TICKET"))).andExpect(status().isNoContent());
        mvc.perform(get("/student/draft").with(user("STU001").roles("Student"))).andExpect(status().isForbidden());
        mvc.perform(post("/student/draft").contentType(MediaType.APPLICATION_JSON).content("{}").with(student)).andExpect(status().isForbidden());
        assertEquals("My private draft",drafts.findById(accounts.findByUniversityId("STU001").orElseThrow().getUserId()).orElseThrow().getSubject());
    }
    @Test void draftValidationRejectsOversizedInput() throws Exception {
        mvc.perform(post("/student/draft").with(user("STU001").authorities(()->"ROLE_Student",()->"SUBMIT_TICKET")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\""+"x".repeat(201)+"\"}")).andExpect(status().isBadRequest());
        assertFalse(drafts.existsById(accounts.findByUniversityId("STU001").orElseThrow().getUserId()));
    }
    @Test void successfulTicketSubmissionClearsOnlyItsOwnersDraft(){
        for(String id:new String[]{"STU001","STU002"}){TicketDraft d=new TicketDraft();d.setUserId(accounts.findByUniversityId(id).orElseThrow().getUserId());d.setSubject("Saved work");drafts.saveAndFlush(d);}
        ticket(null);
        assertFalse(drafts.existsById(accounts.findByUniversityId("STU001").orElseThrow().getUserId()));
        assertTrue(drafts.existsById(accounts.findByUniversityId("STU002").orElseThrow().getUserId()));
    }
    @Test void disabledSavedRepliesAreExcludedFromTheTicketComposer() throws Exception {
        var enabled=tools.saveReply(null,"Screenshot request","Please attach a screenshot.",true,"ADM001","127.0.0.1");
        var disabled=tools.saveReply(null,"Old response","Retired reply",false,"ADM001","127.0.0.1");Ticket t=ticket(null);
        mvc.perform(get("/admin/tickets/"+t.getTicketId()).with(user("ADM001").authorities(()->"ROLE_System Administrator",()->"VIEW_ALL_TICKETS"))).andExpect(status().isOk()).andExpect(content().string(not(containsString("id=\"comment-text\""))));
        mvc.perform(get("/admin/tickets/"+t.getTicketId()).with(user("ADM001").authorities(()->"ROLE_System Administrator",()->"VIEW_ALL_TICKETS",()->"UPDATE_TICKET"))).andExpect(status().isOk()).andExpect(content().string(containsString(enabled.getTitle()))).andExpect(content().string(not(containsString(disabled.getTitle()))));
        mvc.perform(get("/student/tickets/"+t.getTicketId()).with(user("STU001").authorities(()->"ROLE_Student",()->"VIEW_OWN_TICKETS"))).andExpect(status().isOk()).andExpect(content().string(not(containsString(enabled.getMessage()))));
    }
    @Test void overdueAlertsNotifyAssignedStaffAndManagerOnceWithEmail(){
        Department d=new Department();d.setDepartmentName(UUID.randomUUID().toString());departments.save(d);member("SUP001",d);member("MGR001",d);
        Ticket t=ticket(d);t.setCreatedDate(LocalDateTime.now().minusDays(10));tickets.saveAndFlush(t);
        TicketAssignment a=new TicketAssignment();a.setTicket(t);a.setAssignedToUser(accounts.findByUniversityId("SUP001").orElseThrow());a.setAssignedByUser(accounts.findByUniversityId("ADM001").orElseThrow());assignments.saveAndFlush(a);
        long baseline=emails.count();assertEquals(2,alerts.alert(t.getTicketId(),LocalDateTime.now()));assertEquals(0,alerts.alert(t.getTicketId(),LocalDateTime.now()));assertEquals(baseline+2,emails.count());
        assertEquals(2,notifications.findAll().stream().filter(n->n.getNotificationType()==NotificationType.OVERDUE).count());
        t.setStatus(TicketStatus.RESOLVED);tickets.saveAndFlush(t);assertEquals(0,alerts.alert(t.getTicketId(),LocalDateTime.now()));
    }
    @Test void overdueAlertsRespectPreferencesAndFallbackToAdministrator(){
        Ticket t=ticket(null);t.setCreatedDate(LocalDateTime.now().minusDays(10));tickets.saveAndFlush(t);
        Long admin=accounts.findByUniversityId("ADM001").orElseThrow().getUserId();preferences.updatePreference(admin,false,false,"127.0.0.1");
        assertEquals(0,alerts.alert(t.getTicketId(),LocalDateTime.now()));preferences.updatePreference(admin,true,false,"127.0.0.1");assertEquals(1,alerts.alert(t.getTicketId(),LocalDateTime.now()));
    }
}
