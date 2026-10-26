package com.university.helpdesk.controller;

import com.university.helpdesk.entity.FaqStatus;
import com.university.helpdesk.repository.FaqRepository;
import com.university.helpdesk.service.FaqService;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class FaqSuggestionController {
    private final FaqRepository faqs;
    private final FaqService service;
    public FaqSuggestionController(FaqRepository faqs, FaqService service) { this.faqs = faqs; this.service = service; }
    public record Suggestion(Long id, String question, String excerpt) {}
    @GetMapping("/faq/suggestions")
    @Transactional(readOnly = true)
    public List<Suggestion> suggestions(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long categoryId) {
        String query = service.normalizeQuery(q);
        if (categoryId != null && service.getActiveCategory(categoryId).isEmpty()) return List.of();
        if (query.isBlank() && categoryId == null) return List.of();
        return faqs.findSuggestions(FaqStatus.PUBLISHED, query, categoryId, PageRequest.of(0, 4)).stream()
                .map(f -> new Suggestion(f.getFaqId(), f.getQuestion(), f.getAnswer().substring(0, Math.min(180, f.getAnswer().length())))).toList();
    }
}
