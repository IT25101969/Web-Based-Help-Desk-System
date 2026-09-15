package com.university.helpdesk.controller;

import com.university.helpdesk.entity.Category;
import com.university.helpdesk.entity.Faq;
import com.university.helpdesk.service.FaqService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/faq")
public class FaqController {

    private final FaqService faqService;

    public FaqController(FaqService faqService) {
        this.faqService = faqService;
    }

    @GetMapping
    public String list(
            @RequestParam(value = "q", required = false) String rawQuery,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Authentication authentication,
            Model model
    ) {
        String normalizedQuery = faqService.normalizeQuery(rawQuery);
        String displayQuery = normalizedQuery == null ? "" : normalizedQuery;

        Optional<Category> activeCategory = faqService.getActiveCategory(categoryId);
        Long activeCategoryId = activeCategory.map(Category::getCategoryId).orElse(null);

        boolean invalidCategoryAttempted = categoryId != null && activeCategory.isEmpty();

        List<Faq> faqs = faqService.searchPublishedFaqs(normalizedQuery, activeCategoryId);

        String username = (authentication != null && authentication.isAuthenticated())
                ? authentication.getName()
                : null;

        faqService.logSearch(normalizedQuery, activeCategory.orElse(null), faqs.size(), username);

        model.addAttribute("faqs", faqs);
        model.addAttribute("query", displayQuery);
        model.addAttribute("categories", faqService.getActiveCategories());
        model.addAttribute("selectedCategoryId", activeCategoryId);
        model.addAttribute("selectedCategory", activeCategory.orElse(null));
        model.addAttribute("invalidCategoryAttempted", invalidCategoryAttempted);
        model.addAttribute("activeCategoryForTicket", activeCategoryId);

        return "faq";
    }
}
