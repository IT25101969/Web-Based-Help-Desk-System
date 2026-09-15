package com.university.helpdesk.controller;

import com.university.helpdesk.dto.FaqSearchAnalyticsSummary;
import com.university.helpdesk.entity.Faq;
import com.university.helpdesk.entity.FaqStatus;
import com.university.helpdesk.repository.FaqRepository;
import com.university.helpdesk.service.FaqService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/faqs")
public class AdminFaqController {

    private final FaqService faqService;
    private final FaqRepository faqRepository;

    public AdminFaqController(
            FaqService faqService,
            FaqRepository faqRepository
    ) {
        this.faqService = faqService;
        this.faqRepository = faqRepository;
    }

    @GetMapping
    public String list(Model model) {
        List<Faq> faqs = faqRepository.findAll();
        model.addAttribute("faqs", faqs);
        model.addAttribute("categories", faqService.getActiveCategories());
        return "admin-faqs";
    }

    @PostMapping
    public String create(
            @RequestParam(required = false) String question,
            @RequestParam(required = false) String answer,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "DRAFT") FaqStatus status,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            faqService.createFaq(
                    question,
                    answer,
                    categoryId,
                    status,
                    authentication != null ? authentication.getName() : null,
                    request != null ? request.getRemoteAddr() : null
            );
            redirectAttributes.addFlashAttribute("success", "FAQ created successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }

    @PostMapping("/{faqId}")
    public String update(
            @PathVariable Long faqId,
            @RequestParam(required = false) String question,
            @RequestParam(required = false) String answer,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) FaqStatus status,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            faqService.updateFaq(
                    faqId,
                    question,
                    answer,
                    categoryId,
                    status,
                    authentication != null ? authentication.getName() : null,
                    request != null ? request.getRemoteAddr() : null
            );
            redirectAttributes.addFlashAttribute("success", "FAQ updated successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }

    @PostMapping("/{faqId}/publish")
    public String publish(
            @PathVariable Long faqId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            faqService.publishFaq(
                    faqId,
                    authentication != null ? authentication.getName() : null,
                    request != null ? request.getRemoteAddr() : null
            );
            redirectAttributes.addFlashAttribute("success", "FAQ published successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }

    @PostMapping("/{faqId}/archive")
    public String archive(
            @PathVariable Long faqId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            faqService.archiveFaq(
                    faqId,
                    authentication != null ? authentication.getName() : null,
                    request != null ? request.getRemoteAddr() : null
            );
            redirectAttributes.addFlashAttribute("success", "FAQ archived successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }

    @PostMapping("/{faqId}/delete")
    public String delete(
            @PathVariable Long faqId,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            faqService.deleteFaq(
                    faqId,
                    authentication != null ? authentication.getName() : null,
                    request != null ? request.getRemoteAddr() : null
            );
            redirectAttributes.addFlashAttribute("success", "FAQ deleted successfully.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/faqs";
    }

    @GetMapping("/search-analytics")
    public String searchAnalytics(Model model) {
        FaqSearchAnalyticsSummary summary = faqService.getSearchAnalytics();
        model.addAttribute("analytics", summary);
        return "admin-faq-search-analytics";
    }
}
