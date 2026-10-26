package com.university.helpdesk;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class SiteReviewRegressionTests extends TestAccounts {
    @Autowired TicketService service;
    @Autowired CategoryRepository categories;
    @Autowired TicketRepository tickets;
    @Autowired UserAccountRepository accounts;
    @Autowired IncidentRepository incidents;
    @Autowired TicketStatusHistoryRepository history;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired AccountCreationService accountCreation;
    @Autowired RoleRepository roles;
    @Autowired CommentService comments;
    @Autowired FaqService faqs;
    @Autowired FaqRepository faqRecords;
    @Autowired UserCommentRepository commentRecords;
    @Test void oversizedCommentsAndFaqAnswersLeaveNoPartialRecords() {
        var t=create(category().getCategoryId(),"Detail","High","Description");
        long commentsBefore=commentRecords.count(), faqsBefore=faqRecords.count();
        assertThrows(IllegalArgumentException.class,()->comments.addComment(t.getTicketId(),"STU001","x".repeat(20001),CommentType.PUBLIC));
        assertThrows(IllegalArgumentException.class,()->faqs.createFaq("Question","x".repeat(20001),null,FaqStatus.DRAFT,"ADM001",null));
        assertEquals(commentsBefore,commentRecords.count()); assertEquals(faqsBefore,faqRecords.count());
    }
    @Test void missingTicketsReturnNotFoundInsteadOfServerErrors() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/student/tickets/9223372036854775807")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("STU001").authorities(()->"ROLE_Student",()->"VIEW_OWN_TICKETS")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/tickets/9223372036854775807")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ADM001").authorities(()->"ROLE_System Administrator",()->"VIEW_ALL_TICKETS")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
    @Test void invalidAccountFieldsCannotProducePartialSqlRecords(){
        long baseline=accounts.count();Role role=roles.findByRoleName("Student").orElseThrow();
        UserAccount a=new UserAccount();a.setUniversityId("x".repeat(51));a.setEmail("review@example.invalid");a.setFirstName("Review");a.setLastName("Account");a.setPasswordHash("Unused verification hash");
        assertThrows(IllegalArgumentException.class,()->accountCreation.create(a,role));
        a.setUniversityId("REVIEW_VALID");a.setEmail("invalid email");
        assertThrows(IllegalArgumentException.class,()->accountCreation.create(a,role));assertEquals(baseline,accounts.count());
    }
    @Test void serviceRequestDetailsSurviveAnUnsuccessfulSubmission() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/student/tickets")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("STU001").authorities(()->"ROLE_Student",()->"SUBMIT_TICKET"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .param("categoryId",category().getCategoryId().toString()).param("ticketType","SERVICE_REQUEST")
                .param("subject","").param("description","Request description").param("subtypeDetail","Transcript verification"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("value=\"Transcript verification\"")));
    }
    Category category(){Category c=new Category();c.setCategoryName("Review category");return categories.saveAndFlush(c);}
    Ticket create(Long categoryId,String subtype,String severity,String description){return service.createTicket("STU001",categoryId,TicketType.INCIDENT,"Review ticket",description,subtype,severity);}
    @Test void incidentDetailsAtTheFormLimitPersistWithoutSqlTruncation(){
        String detail="x".repeat(150);Ticket t=create(category().getCategoryId(),detail,"Critical","Description");
        entityManager.flush();entityManager.clear();assertEquals(detail,incidents.findById(t.getTicketId()).orElseThrow().getIncidentType());
    }
    @Test void invalidSubmissionLengthsAreRejectedBeforeTicketWrites(){
        Long id=category().getCategoryId();long baseline=tickets.count();
        assertThrows(IllegalArgumentException.class,()->create(id,"x".repeat(151),"High","Description"));
        assertThrows(IllegalArgumentException.class,()->create(id,"Detail","x".repeat(31),"Description"));
        assertThrows(IllegalArgumentException.class,()->create(id,"Detail","High","x".repeat(20001)));
        assertEquals(baseline,tickets.count());
    }
    @Test void inactiveAccountsCannotCreateTicketsDirectly(){
        Long id=category().getCategoryId();var student=accounts.findByUniversityId("STU001").orElseThrow();student.setAccountStatus(AccountStatus.DISABLED);accounts.saveAndFlush(student);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->create(id,"Detail","High","Description"));
    }
    @Test void oversizedStatusReasonDoesNotChangeTicketOrHistory(){
        Ticket t=create(category().getCategoryId(),"Detail","High","Description");long baseline=history.count();
        assertThrows(IllegalArgumentException.class,()->service.changeStatus(t.getTicketId(),TicketStatus.IN_PROGRESS,"ADM001","x".repeat(501)));
        assertEquals(TicketStatus.NEW,tickets.findById(t.getTicketId()).orElseThrow().getStatus());assertEquals(baseline,history.count());
    }
}
