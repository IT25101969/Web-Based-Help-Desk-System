package com.university.helpdesk.controller;

import com.university.helpdesk.service.ItAssetService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MyAssetController {
    private final ItAssetService assets;

    public MyAssetController(ItAssetService assets) { this.assets = assets; }

    @GetMapping("/my-assets")
    public String assigned(Authentication authentication, @RequestParam(defaultValue = "0") int page, Model model) {
        if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_System Administrator".equals(a.getAuthority())))
            return "redirect:/admin/assets";
        model.addAttribute("assetPage", assets.assignedTo(authentication.getName(), page));
        var reports = assets.myReports(authentication.getName());
        model.addAttribute("repairReports", reports);
        model.addAttribute("pendingAssetIds", reports.stream()
                .filter(r -> r.getStatus() == com.university.helpdesk.entity.AssetMaintenanceReport.Status.PENDING)
                .map(r -> r.getAsset().getAssetId()).toList());
        return "my-assets";
    }
    @PostMapping("/my-assets/{id}/maintenance-complete")
    public String complete(@PathVariable Long id, @RequestParam String notes, Authentication auth,
            HttpServletRequest request, RedirectAttributes flash) {
        try {
            assets.completeMaintenance(id, auth.getName(), notes, request.getRemoteAddr());
            flash.addFlashAttribute("success", "Repair report submitted. An administrator has been notified for review.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); flash.addFlashAttribute("repairNotes", notes); }
        return "redirect:/my-assets";
    }
}
