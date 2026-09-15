package com.university.helpdesk.controller;

import com.university.helpdesk.entity.Category;
import com.university.helpdesk.entity.Faq;
import com.university.helpdesk.entity.FaqStatus;
import com.university.helpdesk.repository.CategoryRepository;
import com.university.helpdesk.repository.FaqRepository;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/faqs")
public class AdminFaqController {

    private final FaqRepository faqRepository;
    private final CategoryRepository categoryRepository;

    public AdminFaqController(
            FaqRepository faqRepository,
            CategoryRepository categoryRepository
    ) {
        this.faqRepository = faqRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("faqs", faqRepository.findAll());
        model.addAttribute(
                "categories",
                categoryRepository.findByStatusIgnoreCaseOrderByCategoryNameAsc("ACTIVE")
        );
        return "admin-faqs";
    }

    @PostMapping
    public String create(
            @RequestParam String question,
            @RequestParam String answer,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "DRAFT") FaqStatus status,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (question == null || question.trim().isEmpty()) {
                throw new IllegalArgumentException("Question is required.");
            }
            if (answer == null || answer.trim().isEmpty()) {
                throw new IllegalArgumentException("Answer is required.");
            }

            Faq faq = new Faq();
            faq.setQuestion(question.trim());
            faq.setAnswer(answer.trim());
            faq.setStatus(status != null ? status : FaqStatus.DRAFT);

            if (categoryId != null) {
                Category category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new IllegalArgumentException("Category was not found."));
                faq.setCategory(category);
            }

            faqRepository.save(faq);
            redirectAttributes.addFlashAttribute("success", "FAQ created.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }

    @PostMapping("/{faqId}")
    public String update(
            @PathVariable Long faqId,
            @RequestParam String question,
            @RequestParam String answer,
            @RequestParam(required = false) Long categoryId,
            @RequestParam FaqStatus status,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (question == null || question.trim().isEmpty()) {
                throw new IllegalArgumentException("Question is required.");
            }
            if (answer == null || answer.trim().isEmpty()) {
                throw new IllegalArgumentException("Answer is required.");
            }

            Faq faq = faqRepository.findById(faqId)
                    .orElseThrow(() -> new IllegalArgumentException("FAQ was not found."));

            faq.setQuestion(question.trim());
            faq.setAnswer(answer.trim());
            faq.setStatus(status != null ? status : FaqStatus.DRAFT);
            faq.setCategory(
                    categoryId == null
                            ? null
                            : categoryRepository.findById(categoryId)
                                .orElseThrow(() -> new IllegalArgumentException("Category was not found."))
            );

            faqRepository.save(faq);
            redirectAttributes.addFlashAttribute("success", "FAQ updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }

    @PostMapping("/{faqId}/archive")
    public String archive(
            @PathVariable Long faqId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Faq faq = faqRepository.findById(faqId)
                    .orElseThrow(() -> new IllegalArgumentException("FAQ was not found."));
            faq.setStatus(FaqStatus.ARCHIVED);
            faqRepository.save(faq);
            redirectAttributes.addFlashAttribute("success", "FAQ archived.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }
}
