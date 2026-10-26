package com.university.helpdesk.controller;

import com.university.helpdesk.repository.*;
import com.university.helpdesk.service.ServiceCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/service-setup")
public class ServiceCatalogController {
    private final DepartmentRepository departments;
    private final CategoryRepository categories;
    private final ServiceCatalogService catalog;
    private final UserDepartmentRepository memberships;
    private final UserRoleRepository roles;
    private final com.university.helpdesk.security.AccountSessionService sessions;
    public ServiceCatalogController(DepartmentRepository departments, CategoryRepository categories, ServiceCatalogService catalog,
            UserDepartmentRepository memberships, UserRoleRepository roles, com.university.helpdesk.security.AccountSessionService sessions) {
        this.departments = departments; this.categories = categories; this.catalog = catalog;
        this.memberships = memberships; this.roles = roles; this.sessions = sessions;
    }
    @GetMapping
    public String setup(Model model) {
        var departmentList = departments.findAllByOrderByDepartmentNameAsc();
        var categoryList = categories.findAllByOrderByCategoryNameAsc();
        model.addAttribute("departments", departmentList); model.addAttribute("categories", categoryList);
        model.addAttribute("activeCategories", categoryList.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count());
        model.addAttribute("unmappedCategories", categoryList.stream().filter(c -> "ACTIVE".equals(c.getStatus()) && c.getDepartment() == null).count());
        var memberMap = memberships.findByActiveTrue().stream().collect(java.util.stream.Collectors.groupingBy(m -> m.getDepartment().getDepartmentId()));
        model.addAttribute("departmentMembers", memberMap);
        var eligible = new java.util.TreeMap<String, com.university.helpdesk.entity.UserAccount>();
        for (String role : java.util.List.of("Help Desk Support Staff", "Department Support Team Member", "Department Manager"))
            for (var membership : roles.findByRoleRoleNameAndActiveTrue(role))
                if (membership.getUser().getAccountStatus() == com.university.helpdesk.entity.AccountStatus.ACTIVE)
                    eligible.put(membership.getUser().getUniversityId(), membership.getUser());
        var availableMembers = new java.util.HashMap<Long, java.util.List<com.university.helpdesk.entity.UserAccount>>();
        for (var department : departmentList) {
            var currentIds = memberMap.getOrDefault(department.getDepartmentId(), java.util.List.of()).stream()
                    .map(m -> m.getUser().getUserId()).collect(java.util.stream.Collectors.toSet());
            availableMembers.put(department.getDepartmentId(), eligible.values().stream().filter(u -> !currentIds.contains(u.getUserId())).toList());
        }
        model.addAttribute("availableMembers", availableMembers);
        return "admin-service-setup";
    }
    @PostMapping("/departments")
    public String department(@RequestParam(required = false) Long id, @RequestParam String name,
            @RequestParam(defaultValue = "") String description, @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String location, Authentication authentication, HttpServletRequest request, RedirectAttributes flash) {
        try { catalog.saveDepartment(id, name, description, email, location, authentication.getName(), request.getRemoteAddr()); flash.addFlashAttribute("success", "Department saved."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        catch (DataIntegrityViolationException ex) { flash.addFlashAttribute("error", "Department could not be saved. Check that its name is unique."); }
        return "redirect:/admin/service-setup#departments";
    }
    @PostMapping("/memberships")
    public String membership(@RequestParam Long departmentId, @RequestParam Long userId, @RequestParam boolean active,
            Authentication authentication, HttpServletRequest request, RedirectAttributes flash) {
        try {
            var membership = catalog.updateMembership(departmentId, userId, active, authentication.getName(), request.getRemoteAddr());
            sessions.expire(membership.getUser().getUniversityId());
            flash.addFlashAttribute("success", active ? "Team member added. They must sign in again to refresh their access." : "Team member access removed.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        catch (DataIntegrityViolationException ex) { flash.addFlashAttribute("error", "Team membership could not be updated. Refresh and try again."); }
        return "redirect:/admin/service-setup#departments";
    }
    @PostMapping("/categories")
    public String category(@RequestParam(required = false) Long id, @RequestParam String name,
            @RequestParam(defaultValue = "") String description, @RequestParam(required = false) Long departmentId,
            @RequestParam String status, Authentication authentication, HttpServletRequest request, RedirectAttributes flash) {
        try { catalog.saveCategory(id, name, description, departmentId, status, authentication.getName(), request.getRemoteAddr()); flash.addFlashAttribute("success", "Category and routing saved."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        catch (DataIntegrityViolationException ex) { flash.addFlashAttribute("error", "Category could not be saved. Check its name and department."); }
        return "redirect:/admin/service-setup#categories";
    }
}
