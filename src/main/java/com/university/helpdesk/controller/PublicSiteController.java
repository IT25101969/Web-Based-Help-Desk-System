package com.university.helpdesk.controller;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.FaqService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
@Transactional(readOnly = true)
public class PublicSiteController {
    private final CategoryRepository categories;
    private final FaqRepository faqs;
    private final FaqService service;
    public PublicSiteController(CategoryRepository categories, FaqRepository faqs, FaqService service) {
        this.categories = categories; this.faqs = faqs; this.service = service;
    }
    public record Guide(Long id, String question, String answer) {}
    public record Topic(Long id, String name) {}
    public record CampusService(String name, String description, List<Topic> topics) {}

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken))
            return "redirect:/dashboard";
        var groups = new TreeMap<String, List<Category>>();
        for (Category category : service.getActiveCategories()) {
            String name = category.getDepartment() == null ? "General Support" : category.getDepartment().getDepartmentName();
            groups.computeIfAbsent(name, ignored -> new ArrayList<>()).add(category);
        }
        model.addAttribute("services", groups.entrySet().stream().map(entry -> {
            Category first = entry.getValue().getFirst();
            String description = first.getDepartment() == null ? "Help finding the right university support team." : first.getDepartment().getDescription();
            return new CampusService(entry.getKey(), description == null || description.isBlank() ? "Explore campus guidance and request support from this team." : description,
                    entry.getValue().stream().map(c -> new Topic(c.getCategoryId(), c.getCategoryName())).toList());
        }).toList());
        model.addAttribute("guides", publicGuides("", null, 4));
        return "landing";
    }
    @GetMapping("/help")
    public String help(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long categoryId, Model model) {
        String query = service.normalizeQuery(q);
        boolean unavailable = categoryId != null && service.getActiveCategory(categoryId).isEmpty();
        model.addAttribute("query", query); model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("topics", service.getActiveCategories().stream().map(c -> new Topic(c.getCategoryId(), c.getCategoryName())).toList());
        model.addAttribute("guides", unavailable ? List.of() : publicGuides(query, categoryId, 20));
        model.addAttribute("unavailableCategory", unavailable);
        return "public-help";
    }
    private List<Guide> publicGuides(String query, Long categoryId, int limit) {
        return faqs.findSuggestions(FaqStatus.PUBLISHED, query, categoryId, PageRequest.of(0, limit)).stream()
                .map(f -> new Guide(f.getFaqId(), f.getQuestion(), f.getAnswer())).toList();
    }
    @GetMapping("/privacy")
    public String privacy() { return "public-privacy"; }
}
