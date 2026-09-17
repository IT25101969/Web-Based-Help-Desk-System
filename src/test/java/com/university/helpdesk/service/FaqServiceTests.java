package com.university.helpdesk.service;

import com.university.helpdesk.dto.FaqContentGapDto;
import com.university.helpdesk.dto.FaqSearchAnalyticsSummary;
import com.university.helpdesk.dto.FaqTopSearchTermDto;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.CategoryRepository;
import com.university.helpdesk.repository.FaqRepository;
import com.university.helpdesk.repository.FaqSearchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FaqServiceTests extends com.university.helpdesk.TestAccounts {

    @Autowired
    private FaqService faqService;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private FaqSearchLogRepository searchLogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category activeCategoryNetwork;
    private Category activeCategoryAccounts;
    private Category inactiveCategoryLegacy;

    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        activeCategoryNetwork = new Category();
        activeCategoryNetwork.setCategoryName("Network Services " + uniqueSuffix);
        activeCategoryNetwork.setStatus("ACTIVE");
        activeCategoryNetwork = categoryRepository.save(activeCategoryNetwork);

        activeCategoryAccounts = new Category();
        activeCategoryAccounts.setCategoryName("Student Accounts " + uniqueSuffix);
        activeCategoryAccounts.setStatus("ACTIVE");
        activeCategoryAccounts = categoryRepository.save(activeCategoryAccounts);

        inactiveCategoryLegacy = new Category();
        inactiveCategoryLegacy.setCategoryName("Legacy Systems " + uniqueSuffix);
        inactiveCategoryLegacy.setStatus("INACTIVE");
        inactiveCategoryLegacy = categoryRepository.save(inactiveCategoryLegacy);
    }

    private Faq createFaq(String question, String answer, Category category, FaqStatus status) {
        Faq faq = new Faq();
        faq.setQuestion(question + " " + uniqueSuffix);
        faq.setAnswer(answer);
        faq.setCategory(category);
        faq.setStatus(status);
        return faqRepository.save(faq);
    }

    @Test
    @DisplayName("1. Blank search returns published FAQs")
    void testBlankSearchReturnsPublishedFaqs() {
        Faq pub = createFaq("How to connect eduroam", "Select eduroam and log in", activeCategoryNetwork, FaqStatus.PUBLISHED);

        List<Faq> results = faqService.searchPublishedFaqs("", null);

        assertTrue(results.stream().anyMatch(f -> f.getFaqId().equals(pub.getFaqId())));
    }

    @Test
    @DisplayName("2. Keyword search matches question text")
    void testKeywordSearchMatchesQuestion() {
        Faq faq = createFaq("Configure Outlook Email", "Instructions for Outlook", activeCategoryAccounts, FaqStatus.PUBLISHED);

        List<Faq> results = faqService.searchPublishedFaqs("Outlook Email", null);

        assertTrue(results.stream().anyMatch(f -> f.getFaqId().equals(faq.getFaqId())));
    }

    @Test
    @DisplayName("3. Keyword search matches answer text")
    void testKeywordSearchMatchesAnswer() {
        Faq faq = createFaq("Printer Troubleshooting", "Clear the paper tray jam", activeCategoryNetwork, FaqStatus.PUBLISHED);

        List<Faq> results = faqService.searchPublishedFaqs("paper tray jam", null);

        assertTrue(results.stream().anyMatch(f -> f.getFaqId().equals(faq.getFaqId())));
    }

    @Test
    @DisplayName("4. Keyword search matches category name")
    void testKeywordSearchMatchesCategoryName() {
        Faq faq = createFaq("Proxy Server Settings", "Set port 8080", activeCategoryNetwork, FaqStatus.PUBLISHED);

        List<Faq> results = faqService.searchPublishedFaqs("Network Services", null);

        assertTrue(results.stream().anyMatch(f -> f.getFaqId().equals(faq.getFaqId())));
    }

    @Test
    @DisplayName("5. Search is case-insensitive")
    void testSearchIsCaseInsensitive() {
        Faq faq = createFaq("VPN Remote Access", "Use Cisco AnyConnect", activeCategoryNetwork, FaqStatus.PUBLISHED);

        List<Faq> lower = faqService.searchPublishedFaqs("cisco anyconnect", null);
        List<Faq> upper = faqService.searchPublishedFaqs("CISCO ANYCONNECT", null);

        assertTrue(lower.stream().anyMatch(f -> f.getFaqId().equals(faq.getFaqId())));
        assertTrue(upper.stream().anyMatch(f -> f.getFaqId().equals(faq.getFaqId())));
    }

    @Test
    @DisplayName("6 & 7. DRAFT and ARCHIVED FAQs are never returned")
    void testDraftAndArchivedFaqsNeverReturned() {
        Faq draft = createFaq("Draft Secret Question", "Secret Answer", activeCategoryNetwork, FaqStatus.DRAFT);
        Faq archived = createFaq("Old Discontinued System", "Old Answer", activeCategoryNetwork, FaqStatus.ARCHIVED);

        List<Faq> results = faqService.searchPublishedFaqs("Secret Question", null);
        assertFalse(results.stream().anyMatch(f -> f.getFaqId().equals(draft.getFaqId())));

        List<Faq> archivedResults = faqService.searchPublishedFaqs("Discontinued System", null);
        assertFalse(archivedResults.stream().anyMatch(f -> f.getFaqId().equals(archived.getFaqId())));
    }

    @Test
    @DisplayName("8. PUBLISHED FAQ is returned in search results")
    void testPublishedFaqReturned() {
        Faq published = createFaq("Campus Shuttle Schedule", "Bus arrives every 15 minutes", null, FaqStatus.PUBLISHED);

        List<Faq> results = faqService.searchPublishedFaqs("Shuttle Schedule", null);

        assertTrue(results.stream().anyMatch(f -> f.getFaqId().equals(published.getFaqId())));
    }

    @Test
    @DisplayName("9. Category filter returns only selected category")
    void testCategoryFilterReturnsOnlySelectedCategory() {
        Faq netFaq = createFaq("Wi-Fi Setup", "Connect to Wi-Fi", activeCategoryNetwork, FaqStatus.PUBLISHED);
        Faq accFaq = createFaq("ID Card Balance", "Top up at finance", activeCategoryAccounts, FaqStatus.PUBLISHED);

        List<Faq> results = faqService.searchPublishedFaqs(null, activeCategoryNetwork.getCategoryId());

        assertTrue(results.stream().anyMatch(f -> f.getFaqId().equals(netFaq.getFaqId())));
        assertFalse(results.stream().anyMatch(f -> f.getFaqId().equals(accFaq.getFaqId())));
    }

    @Test
    @DisplayName("10. Keyword + category combined filtering works")
    void testKeywordAndCategoryCombinedFiltering() {
        Faq netFaq = createFaq("Password Reset on Network", "Reset via portal", activeCategoryNetwork, FaqStatus.PUBLISHED);
        Faq accFaq = createFaq("Password Reset on Portal", "Reset via accounts desk", activeCategoryAccounts, FaqStatus.PUBLISHED);

        List<Faq> results = faqService.searchPublishedFaqs("Password Reset", activeCategoryNetwork.getCategoryId());

        assertTrue(results.stream().anyMatch(f -> f.getFaqId().equals(netFaq.getFaqId())));
        assertFalse(results.stream().anyMatch(f -> f.getFaqId().equals(accFaq.getFaqId())));
    }

    @Test
    @DisplayName("11. Invalid category handled safely")
    void testInvalidCategoryHandledSafely() {
        Optional<Category> invalid = faqService.getActiveCategory(999999L);
        assertTrue(invalid.isEmpty());
    }

    @Test
    @DisplayName("12. Inactive category not exposed for prefill")
    void testInactiveCategoryNotExposed() {
        Optional<Category> inactive = faqService.getActiveCategory(inactiveCategoryLegacy.getCategoryId());
        assertTrue(inactive.isEmpty());
    }

    @Test
    @DisplayName("13. No-result search returns clean empty list")
    void testNoResultStateReturnsEmptyList() {
        List<Faq> results = faqService.searchPublishedFaqs("NonExistentTermXYZ12345", null);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("14 & 15. Search log created for meaningful query and stores results count")
    void testSearchLogCreatedForMeaningfulQuery() {
        long beforeCount = searchLogRepository.count();

        faqService.logSearch("VPN Configuration", activeCategoryNetwork, 3, "STU001");

        long afterCount = searchLogRepository.count();
        assertEquals(beforeCount + 1, afterCount);

        List<FaqSearchLog> logs = searchLogRepository.findAll();
        FaqSearchLog lastLog = logs.get(logs.size() - 1);
        assertEquals("VPN Configuration", lastLog.getQuery());
        assertEquals("vpn configuration", lastLog.getNormalizedQuery());
        assertEquals(3, lastLog.getResultsCount());
        assertEquals(activeCategoryNetwork.getCategoryId(), lastLog.getCategory().getCategoryId());
        assertNotNull(lastLog.getUser());
        assertEquals("STU001", lastLog.getUser().getUniversityId());
    }

    @Test
    @DisplayName("16 & 17. Zero-result search logged and counted in analytics")
    void testZeroResultSearchLogged() {
        long zeroBefore = searchLogRepository.countByResultsCount(0);

        faqService.logSearch("Missing Topic " + uniqueSuffix, null, 0, "STU001");

        long zeroAfter = searchLogRepository.countByResultsCount(0);
        assertEquals(zeroBefore + 1, zeroAfter);
    }

    @Test
    @DisplayName("18. Blank page load not logged unnecessarily")
    void testBlankPageLoadNotLoggedUnnecessarily() {
        long before = searchLogRepository.count();

        faqService.logSearch("", null, 5, "STU001");
        faqService.logSearch("   ", null, 5, "STU001");
        faqService.logSearch(null, null, 5, "STU001");

        long after = searchLogRepository.count();
        assertEquals(before, after);
    }

    @Test
    @DisplayName("19. Single query normalization collapses spaces and enforces max length")
    void testQueryNormalization() {
        assertEquals("wifi access", faqService.normalizeQuery("   wifi    access   "));
        assertNull(faqService.normalizeQuery(null));
        assertEquals("", faqService.normalizeQuery("   "));

        String longString = "a".repeat(250);
        String normalized = faqService.normalizeQuery(longString);
        assertEquals(200, normalized.length());
    }

    @Test
    @DisplayName("20 & 21. Search aggregation, ranking, and zero-result content gaps")
    void testSearchAnalyticsAggregation() {
        String gapTerm = "Quantum Computing Help " + uniqueSuffix;
        String popularTerm = "Campus Wi-Fi " + uniqueSuffix;

        // 3 searches for popularTerm with results
        faqService.logSearch(popularTerm, activeCategoryNetwork, 5, "STU001");
        faqService.logSearch("  campus   wi-fi " + uniqueSuffix + "  ", activeCategoryNetwork, 5, "STU001");
        faqService.logSearch("CAMPUS WI-FI " + uniqueSuffix, activeCategoryNetwork, 4, "STU001");

        // 2 searches for gapTerm with 0 results
        faqService.logSearch(gapTerm, null, 0, "STU001");
        faqService.logSearch("quantum computing help " + uniqueSuffix, null, 0, "STU001");

        FaqSearchAnalyticsSummary summary = faqService.getSearchAnalytics();
        assertNotNull(summary);
        assertTrue(summary.getTotalSearches() >= 5);
        assertTrue(summary.getZeroResultSearches() >= 2);

        // Verify frequent terms ranking
        List<FaqTopSearchTermDto> topTerms = summary.getTopSearchTerms();
        assertTrue(topTerms.stream().anyMatch(t -> t.getNormalizedTerm().contains("campus wi-fi " + uniqueSuffix.toLowerCase())));

        // Verify zero-result content gap highlight
        List<FaqContentGapDto> contentGaps = summary.getContentGaps();
        assertTrue(contentGaps.stream().anyMatch(g -> g.getNormalizedTerm().contains("quantum computing help " + uniqueSuffix.toLowerCase())));
    }

    @Test
    @DisplayName("22 & 23 & 24. Admin create FAQ validation")
    void testAdminCreateFaqValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                faqService.createFaq("", "Valid Answer", null, FaqStatus.DRAFT, "ADM001", "127.0.0.1")
        );
        assertThrows(IllegalArgumentException.class, () ->
                faqService.createFaq("   ", "Valid Answer", null, FaqStatus.DRAFT, "ADM001", "127.0.0.1")
        );
        assertThrows(IllegalArgumentException.class, () ->
                faqService.createFaq("Valid Question", "", null, FaqStatus.DRAFT, "ADM001", "127.0.0.1")
        );
        assertThrows(IllegalArgumentException.class, () ->
                faqService.createFaq("Valid Question", "   ", null, FaqStatus.DRAFT, "ADM001", "127.0.0.1")
        );
        assertThrows(IllegalArgumentException.class, () ->
                faqService.createFaq("a".repeat(301), "Valid Answer", null, FaqStatus.DRAFT, "ADM001", "127.0.0.1")
        );

        Faq created = faqService.createFaq("Valid Question " + uniqueSuffix, "Valid Answer", activeCategoryNetwork.getCategoryId(), FaqStatus.PUBLISHED, "ADM001", "127.0.0.1");
        assertNotNull(created.getFaqId());
        assertEquals(FaqStatus.PUBLISHED, created.getStatus());
    }

    @Test
    @DisplayName("25, 26, 27. Admin publish, archive, and immediate disappearance from user search")
    void testAdminPublishAndArchiveFaq() {
        Faq created = faqService.createFaq("Lifecycle Test Question " + uniqueSuffix, "Lifecycle Answer", null, FaqStatus.DRAFT, "ADM001", "127.0.0.1");

        // DRAFT not visible
        List<Faq> draftSearch = faqService.searchPublishedFaqs("Lifecycle Test Question " + uniqueSuffix, null);
        assertFalse(draftSearch.stream().anyMatch(f -> f.getFaqId().equals(created.getFaqId())));

        // Publish
        Faq published = faqService.publishFaq(created.getFaqId(), "ADM001", "127.0.0.1");
        assertEquals(FaqStatus.PUBLISHED, published.getStatus());

        List<Faq> pubSearch = faqService.searchPublishedFaqs("Lifecycle Test Question " + uniqueSuffix, null);
        assertTrue(pubSearch.stream().anyMatch(f -> f.getFaqId().equals(created.getFaqId())));

        // Archive
        Faq archived = faqService.archiveFaq(created.getFaqId(), "ADM001", "127.0.0.1");
        assertEquals(FaqStatus.ARCHIVED, archived.getStatus());

        // Immediately disappears from user results
        List<Faq> archSearch = faqService.searchPublishedFaqs("Lifecycle Test Question " + uniqueSuffix, null);
        assertFalse(archSearch.stream().anyMatch(f -> f.getFaqId().equals(created.getFaqId())));
    }
}
