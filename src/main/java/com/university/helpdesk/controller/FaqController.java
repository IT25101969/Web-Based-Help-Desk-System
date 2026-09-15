package com.university.helpdesk.controller;

import com.university.helpdesk.entity.FaqStatus;
import com.university.helpdesk.repository.CategoryRepository;
import com.university.helpdesk.repository.FaqRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/faq")
public class FaqController {

    private final FaqRepository faqRepository;
    private final CategoryRepository categoryRepository;

    public FaqController(
            FaqRepository faqRepository,
            CategoryRepository categoryRepository
    ) {
        this.faqRepository = faqRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String list(
            @RequestParam(value = "q", required = false) String query,
            Model model
    ) {
        String search = query == null ? "" : query.trim();

        if (search.isBlank()) {
            model.addAttribute(
                    "faqs",
                    faqRepository.findByStatusOrderByQuestionAsc(FaqStatus.PUBLISHED)
            );
        } else {
            model.addAttribute(
                    "faqs",
                    faqRepository.findByStatusAndQuestionContainingIgnoreCaseOrderByQuestionAsc(
                            FaqStatus.PUBLISHED,
                            search
                    )
            );
        }

        model.addAttribute("query", search);
        model.addAttribute(
                "categories",
                categoryRepository.findByStatusIgnoreCaseOrderByCategoryNameAsc("ACTIVE")
        );

        return "faq";
    }
}
