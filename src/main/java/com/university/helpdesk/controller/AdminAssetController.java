package com.university.helpdesk.controller;

import com.university.helpdesk.dto.AssetForm;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.ItAssetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/assets")
public class AdminAssetController {
    private final ItAssetService assets;
    private final DepartmentRepository departments;
    private final UserAccountRepository users;
    public AdminAssetController(ItAssetService assets, DepartmentRepository departments, UserAccountRepository users) {
        this.assets = assets; this.departments = departments; this.users = users;
    }
    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) AssetCondition condition, @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("assetPage", assets.search(q, type, condition, departmentId, page));
        model.addAttribute("q", com.university.helpdesk.service.TicketWorkspaceService.normalizeQuery(q));
        model.addAttribute("selectedType", type); model.addAttribute("selectedCondition", condition);
        model.addAttribute("selectedDepartment", departmentId); model.addAttribute("totalAssets", assets.count());
        model.addAttribute("workingAssets", assets.count(AssetCondition.WORKING));
        model.addAttribute("maintenanceAssets", assets.count(AssetCondition.UNDER_MAINTENANCE));
        model.addAttribute("retiredAssets", assets.count(AssetCondition.RETIRED));
        options(model, null);
        return "admin-assets";
    }
    @GetMapping("/new")
    public String createForm(Model model) { return editor(model, null, new AssetForm()); }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("repairReports", assets.repairHistory(id));
        model.addAttribute("asset", assets.get(id)); model.addAttribute("history", assets.history(id, page));
        return "admin-asset-view";
    }
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) { return editor(model, id, AssetForm.from(assets.get(id))); }
    @PostMapping
    public String create(@Valid @ModelAttribute("form") AssetForm form, BindingResult result,
            Authentication auth, HttpServletRequest request, Model model, RedirectAttributes flash) {
        return save(null, form, result, auth, request, model, flash);
    }
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") AssetForm form, BindingResult result,
            Authentication auth, HttpServletRequest request, Model model, RedirectAttributes flash) {
        return save(id, form, result, auth, request, model, flash);
    }
    private String save(Long id, AssetForm form, BindingResult result, Authentication auth, HttpServletRequest request,
            Model model, RedirectAttributes flash) {
        if (result.hasErrors()) return editor(model, id, form);
        try {
            var asset = assets.save(id, form, auth.getName(), request.getRemoteAddr());
            flash.addFlashAttribute("success", id == null ? "Asset registered successfully." : "Asset updated successfully.");
            return "redirect:/admin/assets/" + asset.getAssetId();
        } catch (IllegalArgumentException ex) {
            result.reject("asset", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            result.reject("asset", "This asset tag or serial number is already registered. Check the inventory and try again.");
        }
        return editor(model, id, form);
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, HttpServletRequest request, RedirectAttributes flash) {
        try {
            assets.delete(id, auth.getName(), request.getRemoteAddr()); flash.addFlashAttribute("success", "Asset deleted successfully.");
            return "redirect:/admin/assets";
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            flash.addFlashAttribute("error", ex instanceof IllegalArgumentException ? ex.getMessage() : "This asset is in use. Retire it to preserve its history.");
            return "redirect:/admin/assets/" + id;
        }
    }
    @PostMapping("/{id}/repairs/{reportId}/review")
    public String review(@PathVariable Long id, @PathVariable Long reportId, @RequestParam String decision,
            @RequestParam String notes, Authentication auth, HttpServletRequest request, RedirectAttributes flash) {
        try {
            if (!"approve".equals(decision) && !"return".equals(decision)) throw new IllegalArgumentException("Choose a valid review decision.");
            assets.reviewMaintenance(id, reportId, auth.getName(), "approve".equals(decision), notes, request.getRemoteAddr());
            flash.addFlashAttribute("success", "approve".equals(decision) ? "Repair approved. Equipment is now working." : "Further work requested. The staff member has been notified.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/assets/" + id;
    }
    private String editor(Model model, Long id, AssetForm form) {
        model.addAttribute("form", form); model.addAttribute("assetId", id); options(model, form.getAssignedUserId());
        return "admin-asset-form";
    }
    private void options(Model model, Long assignedUserId) {
        model.addAttribute("departments", departments.findAllByOrderByDepartmentNameAsc());
        model.addAttribute("assetTypes", AssetType.values()); model.addAttribute("assetConditions", AssetCondition.values());
        model.addAttribute("assignableUsers", users.findAll().stream()
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE || u.getUserId().equals(assignedUserId))
                .sorted(java.util.Comparator.comparing(UserAccount::getUniversityId)).toList());
    }
}
