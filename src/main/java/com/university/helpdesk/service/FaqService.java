package com.university.helpdesk.service;

import com.university.helpdesk.dto.*;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FaqService {

    private final FaqRepository faqRepository;
    private final FaqSearchLogRepository searchLogRepository;
    private final CategoryRepository categoryRepository;
    private final UserAccountRepository userAccountRepository;
    private final ActivityLogService activityLogService;

    public FaqService(
            FaqRepository faqRepository,
            FaqSearchLogRepository searchLogRepository,
            CategoryRepository categoryRepository,
            UserAccountRepository userAccountRepository,
            ActivityLogService activityLogService
    ) {
        this.faqRepository = faqRepository;
        this.searchLogRepository = searchLogRepository;
        this.categoryRepository = categoryRepository;
        this.userAccountRepository = userAccountRepository;
        this.activityLogService = activityLogService;
    }

    /**
     * Single query normalization:
     * - trims whitespace
     * - collapses multiple whitespace characters to a single space
     * - enforces maximum 200 characters
     */
    public String normalizeQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String collapsed = trimmed.replaceAll("\\s+", " ");
        if (collapsed.length() > 200) {
            collapsed = collapsed.substring(0, 200).trim();
        }
        return collapsed;
    }

    @Transactional(readOnly = true)
    public Optional<Category> getActiveCategory(Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return categoryRepository.findById(categoryId)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()));
    }

    @Transactional(readOnly = true)
    public List<Category> getActiveCategories() {
        return categoryRepository.findByStatusIgnoreCaseOrderByCategoryNameAsc("ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<Faq> searchPublishedFaqs(String normalizedQuery, Long activeCategoryId) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            if (activeCategoryId == null) {
                return faqRepository.findByStatusOrderByQuestionAsc(FaqStatus.PUBLISHED);
            } else {
                return faqRepository.findByStatusAndCategoryCategoryIdOrderByQuestionAsc(
                        FaqStatus.PUBLISHED,
                        activeCategoryId
                );
            }
        }

        return faqRepository.searchPublished(
                FaqStatus.PUBLISHED,
                normalizedQuery,
                activeCategoryId
        );
    }

    public void logSearch(
            String normalizedQuery,
            Category activeCategory,
            int resultsCount,
            String universityId
    ) {
        boolean hasQuery = normalizedQuery != null && !normalizedQuery.isBlank();
        boolean hasCategory = activeCategory != null;

        // Skip completely blank page visits
        if (!hasQuery && !hasCategory) {
            return;
        }

        FaqSearchLog log = new FaqSearchLog();
        log.setQuery(hasQuery ? normalizedQuery : null);
        log.setNormalizedQuery(hasQuery ? normalizedQuery.toLowerCase() : null);
        log.setCategory(activeCategory);
        log.setResultsCount(resultsCount);
        log.setSearchedAt(LocalDateTime.now());

        if (universityId != null && !universityId.isBlank() && !"anonymousUser".equals(universityId)) {
            userAccountRepository.findByUniversityId(universityId).ifPresent(log::setUser);
        }

        searchLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public FaqSearchAnalyticsSummary getSearchAnalytics() {
        long totalSearches = searchLogRepository.count();
        long zeroResultSearches = searchLogRepository.countByResultsCount(0);

        List<Object[]> rawTopTerms = searchLogRepository.findTopSearchTermsRaw(PageRequest.of(0, 10));
        List<FaqTopSearchTermDto> topTerms = new ArrayList<>();
        for (Object[] row : rawTopTerms) {
            String displayTerm = (String) row[0];
            String normalizedTerm = (String) row[1];
            Long count = row[2] == null ? 0L : ((Number) row[2]).longValue();
            Long zeroCount = row[3] == null ? 0L : ((Number) row[3]).longValue();
            Double avgResults = row[4] == null ? 0.0 : ((Number) row[4]).doubleValue();
            topTerms.add(new FaqTopSearchTermDto(displayTerm, normalizedTerm, count, zeroCount, avgResults));
        }

        List<Object[]> rawGaps = searchLogRepository.findContentGapsRaw(PageRequest.of(0, 10));
        List<FaqContentGapDto> contentGaps = new ArrayList<>();
        for (Object[] row : rawGaps) {
            String displayTerm = (String) row[0];
            String normalizedTerm = (String) row[1];
            Long count = row[2] == null ? 0L : ((Number) row[2]).longValue();
            contentGaps.add(new FaqContentGapDto(displayTerm, normalizedTerm, count));
        }

        List<Object[]> rawCategories = searchLogRepository.findCategoryDistributionRaw(PageRequest.of(0, 10));
        List<FaqCategoryDistributionDto> categoryDist = new ArrayList<>();
        for (Object[] row : rawCategories) {
            String catName = (String) row[0];
            Long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            categoryDist.add(new FaqCategoryDistributionDto(catName, count));
        }

        List<FaqSearchLog> recentSearches = searchLogRepository.findRecentSearches(PageRequest.of(0, 50));

        return new FaqSearchAnalyticsSummary(
                totalSearches,
                zeroResultSearches,
                topTerms,
                contentGaps,
                categoryDist,
                recentSearches
        );
    }

    public Faq createFaq(
            String question,
            String answer,
            Long categoryId,
            FaqStatus status,
            String adminUniversityId,
            String ipAddress
    ) {
        validateFaqInput(question, answer);

        Faq faq = new Faq();
        faq.setQuestion(question.trim());
        faq.setAnswer(answer.trim());
        faq.setStatus(status != null ? status : FaqStatus.DRAFT);

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected category was not found."));
            faq.setCategory(category);
        }

        faq = faqRepository.save(faq);

        UserAccount adminUser = resolveUser(adminUniversityId);
        activityLogService.log(adminUser, "FAQ_CREATED", "FAQ", faq.getFaqId(), ipAddress);

        if (faq.getStatus() == FaqStatus.PUBLISHED) {
            activityLogService.log(adminUser, "FAQ_PUBLISHED", "FAQ", faq.getFaqId(), ipAddress);
        }

        return faq;
    }

    public Faq updateFaq(
            Long faqId,
            String question,
            String answer,
            Long categoryId,
            FaqStatus status,
            String adminUniversityId,
            String ipAddress
    ) {
        if (faqId == null) {
            throw new IllegalArgumentException("FAQ ID is required.");
        }

        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ was not found."));

        validateFaqInput(question, answer);
        if (status == null) {
            throw new IllegalArgumentException("Valid status is required.");
        }

        FaqStatus oldStatus = faq.getStatus();

        faq.setQuestion(question.trim());
        faq.setAnswer(answer.trim());
        faq.setStatus(status);

        if (categoryId == null) {
            faq.setCategory(null);
        } else {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected category was not found."));
            faq.setCategory(category);
        }

        faq = faqRepository.save(faq);

        UserAccount adminUser = resolveUser(adminUniversityId);
        activityLogService.log(adminUser, "FAQ_UPDATED", "FAQ", faq.getFaqId(), ipAddress);

        if (oldStatus != FaqStatus.PUBLISHED && status == FaqStatus.PUBLISHED) {
            activityLogService.log(adminUser, "FAQ_PUBLISHED", "FAQ", faq.getFaqId(), ipAddress);
        } else if (oldStatus != FaqStatus.ARCHIVED && status == FaqStatus.ARCHIVED) {
            activityLogService.log(adminUser, "FAQ_ARCHIVED", "FAQ", faq.getFaqId(), ipAddress);
        }

        return faq;
    }

    public Faq publishFaq(Long faqId, String adminUniversityId, String ipAddress) {
        if (faqId == null) {
            throw new IllegalArgumentException("FAQ ID is required.");
        }
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ was not found."));

        faq.setStatus(FaqStatus.PUBLISHED);
        faq = faqRepository.save(faq);

        UserAccount adminUser = resolveUser(adminUniversityId);
        activityLogService.log(adminUser, "FAQ_PUBLISHED", "FAQ", faq.getFaqId(), ipAddress);
        return faq;
    }

    public Faq archiveFaq(Long faqId, String adminUniversityId, String ipAddress) {
        if (faqId == null) {
            throw new IllegalArgumentException("FAQ ID is required.");
        }
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ was not found."));

        faq.setStatus(FaqStatus.ARCHIVED);
        faq = faqRepository.save(faq);

        UserAccount adminUser = resolveUser(adminUniversityId);
        activityLogService.log(adminUser, "FAQ_ARCHIVED", "FAQ", faq.getFaqId(), ipAddress);
        return faq;
    }

    public void deleteFaq(Long faqId, String adminUniversityId, String ipAddress) {
        if (faqId == null) {
            throw new IllegalArgumentException("FAQ ID is required.");
        }
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ was not found."));

        faqRepository.delete(faq);

        UserAccount adminUser = resolveUser(adminUniversityId);
        activityLogService.log(adminUser, "FAQ_DELETED", "FAQ", faqId, ipAddress);
    }

    private void validateFaqInput(String question, String answer) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Question is required.");
        }
        if (question.trim().length() > 300) {
            throw new IllegalArgumentException("Question must not exceed 300 characters.");
        }
        if (answer == null || answer.trim().isEmpty()) {
            throw new IllegalArgumentException("Answer is required.");
        }
    }

    private UserAccount resolveUser(String universityId) {
        if (universityId == null || universityId.isBlank()) {
            return null;
        }
        return userAccountRepository.findByUniversityId(universityId).orElse(null);
    }
}
