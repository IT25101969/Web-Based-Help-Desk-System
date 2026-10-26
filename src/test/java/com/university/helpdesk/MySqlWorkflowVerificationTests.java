package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in verification against local MySQL. All application writes roll back after the test. */
@EnabledIfEnvironmentVariable(named="MYSQL_REVIEW",matches="true")
@SpringBootTest(properties={"spring.datasource.url=${DB_URL}","spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate","app.seed.demo=false","app.sla.alerts.enabled=false",
        "app.mail.enabled=false","app.mail.scheduler.enabled=false"})
@Transactional
class MySqlWorkflowVerificationTests {
    @Autowired CategoryRepository categories;
    @Autowired TicketRepository tickets;
    @Autowired IncidentRepository incidents;
    @Autowired TicketStatusHistoryRepository histories;
    @Autowired UserCommentRepository comments;
    @Autowired FeedbackRepository feedback;
    @Autowired AttachmentRepository attachments;
    @Autowired AttachmentService attachmentService;
    @Autowired TicketService service;
    @Autowired CommentService commentService;
    @Autowired FeedbackService feedbackService;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ServiceCatalogService catalog;
    @Autowired SupportToolsService tools;
    @Autowired FaqService faqs;
    @Autowired UserProfileService profiles;
    @Autowired DepartmentRepository departments;
    @Autowired UserAccountRepository users;
    @Autowired UserDepartmentRepository memberships;
    @Autowired CampusAnnouncementRepository notices;
    @Autowired SavedReplyRepository replies;
    @Autowired FaqRepository faqRecords;
    @Test void administrativeChangesPersistAndRelatedRecordsStayLinked() {
        String marker="SQL-"+java.util.UUID.randomUUID();
        var d=catalog.saveDepartment(null,marker,"Review department","review@example.invalid","Room A","ADM001",null);
        var c=catalog.saveCategory(null,marker,"Review routing",d.getDepartmentId(),"ACTIVE","ADM001",null);
        Long deptId=d.getDepartmentId(),categoryId=c.getCategoryId();
        Long studentId=users.findByUniversityId("STU001").orElseThrow().getUserId();
        Long supportId=users.findByUniversityId("SUP001").orElseThrow().getUserId();
        catalog.updateMembership(deptId,supportId,true,"ADM001",null);
        var f=faqs.createFaq(marker,"First answer",categoryId,FaqStatus.DRAFT,"ADM001",null);
        Long faqId=f.getFaqId();
        faqs.publishFaq(faqId,"ADM001",null);
        var a=tools.saveAnnouncement(null,marker,"First notice","INFORMATION",false,null,null,"ADM001",null);
        var r=tools.saveReply(null,marker,"First reply",true,"ADM001",null);
        Long noticeId=a.getId(),replyId=r.getId();
        entityManager.flush();entityManager.clear();
        assertEquals(deptId,categories.findById(categoryId).orElseThrow().getDepartment().getDepartmentId());
        assertTrue(memberships.findByUserUserIdAndDepartmentDepartmentId(supportId,deptId).orElseThrow().getActive());
        assertEquals(FaqStatus.PUBLISHED,faqRecords.findById(faqId).orElseThrow().getStatus());
        catalog.saveDepartment(deptId,marker,"Updated department","review@example.invalid","Room B","ADM001",null);
        catalog.saveCategory(categoryId,marker,"Updated routing",deptId,"INACTIVE","ADM001",null);
        catalog.updateMembership(deptId,supportId,false,"ADM001",null);
        tools.saveAnnouncement(noticeId,marker,"Updated notice","MAINTENANCE",false,null,null,"ADM001",null);
        tools.saveReply(replyId,marker,"Updated reply",false,"ADM001",null);
        var student=users.findById(studentId).orElseThrow();
        profiles.update(studentId,student.getUniversityId(),student.getEmail(),"SQL Review",student.getLastName(),"ADM001",null);
        faqs.deleteFaq(faqId,"ADM001",null);
        entityManager.flush();entityManager.clear();
        assertEquals("Room B",departments.findById(deptId).orElseThrow().getLocation());
        assertEquals("INACTIVE",categories.findById(categoryId).orElseThrow().getStatus());
        assertFalse(memberships.findByUserUserIdAndDepartmentDepartmentId(supportId,deptId).orElseThrow().getActive());
        assertEquals("Updated notice",notices.findById(noticeId).orElseThrow().getMessage());
        assertFalse(replies.findById(replyId).orElseThrow().isEnabled());
        assertEquals("SQL Review",users.findById(studentId).orElseThrow().getFirstName());
        assertFalse(faqRecords.existsById(faqId));
        tools.deleteAnnouncement(noticeId,"ADM001",null);
        entityManager.flush();entityManager.clear();
        assertFalse(notices.existsById(noticeId));
    }
    @Test void longUnicodeTextSurvivesMysqlFlushAndReload() throws Exception {
        String longText = "\uD83D\uDE80".repeat(10000);
        Category category = new Category();
        category.setCategoryName("Long text verification " + java.util.UUID.randomUUID());
        categories.saveAndFlush(category);
        Ticket ticket = service.createTicketWithAttachment("STU001", category.getCategoryId(),
                TicketType.INCIDENT, "Long text verification", longText, "Network", "High", null);
        var comment = commentService.addComment(ticket.getTicketId(), "ADM001", longText, CommentType.INTERNAL);
        Faq faq = new Faq(); faq.setQuestion("Long answer verification"); faq.setAnswer(longText);
        entityManager.persist(faq);
        Long ticketId = ticket.getTicketId(), commentId = comment.getCommentId(), faqId = faq.getFaqId();
        entityManager.flush(); entityManager.clear();
        assertEquals(longText, tickets.findById(ticketId).orElseThrow().getDescription());
        assertEquals(longText, comments.findById(commentId).orElseThrow().getCommentText());
        assertEquals(longText, entityManager.find(Faq.class, faqId).getAnswer());
    }

    @Test void ticketLifecyclePersistsAcrossRealSqlFlushAndReload() throws Exception {
        Category c=new Category();c.setCategoryName("SQL verification "+java.util.UUID.randomUUID());categories.saveAndFlush(c);
        var upload=new org.springframework.mock.web.MockMultipartFile("attachment","sql-verification.txt","text/plain","Attachment verification".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Ticket ticket=service.createTicketWithAttachment("STU001",c.getCategoryId(),TicketType.INCIDENT,"MySQL transaction verification","Rollback-only workflow check","x".repeat(150),"High",upload);
        Long id=ticket.getTicketId();
        commentService.addComment(id,"ADM001","Public workflow verification",CommentType.PUBLIC);
        commentService.addComment(id,"ADM001","Internal workflow verification",CommentType.INTERNAL);
        service.changeStatus(id,TicketStatus.IN_PROGRESS,"ADM001","Start verification");
        service.updatePriority(id,TicketPriority.CRITICAL,"ADM001");
        service.changeStatus(id,TicketStatus.RESOLVED,"ADM001","Resolved verification");
        service.changeStatus(id,TicketStatus.CLOSED,"ADM001","Closed verification");
        feedbackService.submit(id,"STU001",5,"SQL feedback verification");
        entityManager.flush();entityManager.clear();
        Ticket stored=tickets.findById(id).orElseThrow();assertEquals(TicketStatus.CLOSED,stored.getStatus());
        assertEquals(TicketPriority.CRITICAL,stored.getPriority());
        assertNotNull(stored.getResolvedDate());assertNotNull(stored.getClosedDate());
        assertEquals(150,incidents.findById(id).orElseThrow().getIncidentType().length());
        assertEquals(4,histories.findByTicketTicketIdOrderByChangedDateAsc(id).size());
        assertEquals(2,comments.findByTicketTicketIdOrderByCreatedDateAsc(id).size());
        assertEquals(1,commentService.getPublicComments(id).size());
        assertEquals(5,feedback.findByTicketTicketId(id).orElseThrow().getRating());
        var storedAttachments=attachments.findByTicketTicketIdOrderByUploadedDateAsc(id);assertEquals(1,storedAttachments.size());
        try(var stream=attachmentService.load(storedAttachments.get(0)).getInputStream()) {
            assertEquals("Attachment verification",new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
