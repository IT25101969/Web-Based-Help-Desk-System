package com.university.helpdesk;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @Transactional
class PublicSiteTests extends TestAccounts {
    @Autowired WebApplicationContext context;
    @Autowired FaqRepository faqs;
    @Autowired CategoryRepository categories;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void anonymousVisitorsCanOpenPublicPagesWhileWorkspacesRemainProtected() throws Exception {
        for (String path : new String[]{"/", "/help", "/privacy"}) mvc.perform(get(path)).andExpect(status().isOk());
        for (String path : new String[]{"/student/tickets", "/admin/users", "/admin/reports", "/notifications", "/faq/suggestions"})
            mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }
    @Test void authenticatedVisitorsGoToTheirDashboard() throws Exception {
        mvc.perform(get("/").with(user("STU001").roles("Student"))).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/dashboard").with(user("STU001").roles("Student"))).andExpect(redirectedUrl("/student/dashboard"));
    }
    @Test void publicSearchNeverExposesDraftContentAndEscapesPublishedText() throws Exception {
        String marker = UUID.randomUUID().toString();
        for (FaqStatus status : new FaqStatus[]{FaqStatus.PUBLISHED, FaqStatus.DRAFT}) {
            Faq f = new Faq(); f.setQuestion(marker + " " + status); f.setAnswer("<script>alert('test')</script>"); f.setStatus(status); faqs.save(f);
        }
        mvc.perform(get("/help").param("q", marker)).andExpect(status().isOk())
                .andExpect(content().string(containsString(marker + " PUBLISHED")))
                .andExpect(content().string(not(containsString(marker + " DRAFT"))))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))));
    }
    @Test void unavailableCategoryDoesNotFallBackToUnrelatedGuides() throws Exception {
        Category c = new Category(); c.setCategoryName(UUID.randomUUID().toString()); c.setStatus("INACTIVE"); categories.save(c);
        String marker = UUID.randomUUID().toString(); Faq f = new Faq(); f.setCategory(c); f.setQuestion(marker); f.setAnswer("Private draft"); f.setStatus(FaqStatus.PUBLISHED); faqs.save(f);
        mvc.perform(get("/help").param("categoryId", c.getCategoryId().toString())).andExpect(status().isOk())
                .andExpect(content().string(containsString("That service category is unavailable")))
                .andExpect(content().string(not(containsString(marker))));
    }
}
