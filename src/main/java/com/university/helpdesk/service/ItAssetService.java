package com.university.helpdesk.service;

import com.university.helpdesk.dto.AssetForm;
import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ItAssetService {
    public record AssetOption(Long id, String tag, String name, String location) {}
    private final ItAssetRepository assets;
    private final DepartmentRepository departments;
    private final UserAccountRepository users;
    private final TicketRepository tickets;
    private final UserRoleRepository roles;
    private final RolePermissionRepository permissions;
    private final ActivityLogService logs;
    private final Validator validator;
    private final AssetMaintenanceReportRepository repairs;
    private final NotificationRepository notifications;

    public ItAssetService(ItAssetRepository assets, DepartmentRepository departments, UserAccountRepository users,
            TicketRepository tickets, UserRoleRepository roles, RolePermissionRepository permissions,
            ActivityLogService logs, Validator validator, AssetMaintenanceReportRepository repairs, NotificationRepository notifications) {
        this.assets = assets; this.departments = departments; this.users = users; this.tickets = tickets;
        this.roles = roles; this.permissions = permissions; this.logs = logs; this.validator = validator; this.repairs = repairs; this.notifications = notifications;
    }
    private UserAccount administrator(String actor) {
        var user = users.findByUniversityId(actor).orElseThrow(() -> new AccessDeniedException("Account was not found."));
        boolean allowed = user.getAccountStatus() == AccountStatus.ACTIVE && roles.findByUserUserIdAndActiveTrue(user.getUserId()).stream()
                .filter(r -> "System Administrator".equals(r.getRole().getRoleName()))
                .anyMatch(r -> permissions.findByRoleRoleIdAndActiveTrue(r.getRole().getRoleId()).stream()
                        .anyMatch(p -> "MANAGE_SYSTEM".equals(p.getPermission().getPermissionName())));
        if (!allowed) throw new AccessDeniedException("Asset management requires administrator access.");
        return user;
    }
    public ItAsset get(Long id) {
        return assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Asset was not found."));
    }
    public Page<ItAsset> search(String query, AssetType type, AssetCondition condition, Long departmentId, int page) {
        String term = TicketWorkspaceService.normalizeQuery(query).toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Specification<ItAsset> filter = (root, criteria, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!term.isEmpty()) {
                String pattern = "%" + term + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("assetTag")), pattern, '\\'),
                        cb.like(cb.lower(root.get("assetName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("location")), pattern, '\\'),
                        cb.like(cb.lower(root.get("serialNumber")), pattern, '\\')));
            }
            if (type != null) predicates.add(cb.equal(root.get("assetType"), type));
            if (condition != null) predicates.add(cb.equal(root.get("condition"), condition));
            if (departmentId != null) predicates.add(cb.equal(root.get("department").get("departmentId"), departmentId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = assets.findAll(filter, PageRequest.of(Math.max(0, Math.min(page, 100000)), 12, Sort.by("assetTag")));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages())
            return assets.findAll(filter, PageRequest.of(result.getTotalPages() - 1, 12, Sort.by("assetTag")));
        return result;
    }
    @Transactional
    public ItAsset save(Long id, AssetForm form, String actor, String ip) {
        var admin = administrator(actor);
        var errors = validator.validate(form);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.iterator().next().getMessage());
        Long excluded = id == null ? -1L : id;
        String tag = form.getAssetTag().toUpperCase(Locale.ROOT);
        String serial = form.getSerialNumber() == null || form.getSerialNumber().isBlank() ? null : form.getSerialNumber().trim();
        if (assets.existsByAssetTagIgnoreCaseAndAssetIdNot(tag, excluded)) throw new IllegalArgumentException("This asset tag is already registered.");
        if (serial != null && assets.existsBySerialNumberIgnoreCaseAndAssetIdNot(serial, excluded)) throw new IllegalArgumentException("This serial number is already registered.");
        var department = departments.findById(form.getDepartmentId()).orElseThrow(() -> new IllegalArgumentException("Choose an existing department."));
        var asset = id == null ? new ItAsset() : assets.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Asset was not found."));
        if (id != null && !Objects.equals(asset.getVersion(), form.getVersion()))
            throw new IllegalArgumentException("This asset changed since you opened it. Reload the editor before saving.");
        UserAccount assigned = form.getAssignedUserId() == null ? null : users.findById(form.getAssignedUserId())
                .orElseThrow(() -> new IllegalArgumentException("Choose an existing user."));
        if (assigned != null && assigned.getAccountStatus() != AccountStatus.ACTIVE
                && (asset.getAssignedUser() == null || !asset.getAssignedUser().getUserId().equals(assigned.getUserId())))
            throw new IllegalArgumentException("Choose an active user for a new assignment.");
        asset.setAssetTag(tag); asset.setAssetName(form.getAssetName().trim()); asset.setAssetType(form.getAssetType());
        asset.setSerialNumber(serial); asset.setDepartment(department); asset.setLocation(form.getLocation().trim());
        asset.setCondition(form.getCondition()); asset.setAssignedUser(assigned); asset.setPurchaseDate(form.getPurchaseDate());
        asset.setNotes(form.getNotes() == null ? null : form.getNotes().trim());
        assets.saveAndFlush(asset);
        logs.log(admin, id == null ? "ASSET_CREATED" : "ASSET_UPDATED", "IT_ASSET", asset.getAssetId(), ip);
        return asset;
    }
    @Transactional
    public void delete(Long id, String actor, String ip) {
        var admin = administrator(actor);
        var asset = assets.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Asset was not found."));
        if (tickets.existsByAssetAssetId(id)) throw new IllegalArgumentException("This asset has linked tickets. Set its condition to Retired to preserve the support history.");
        if (repairs.existsByAssetAssetId(id)) throw new IllegalArgumentException("This asset has maintenance history. Retire it to preserve the repair records.");
        assets.delete(asset); assets.flush(); logs.log(admin, "ASSET_DELETED", "IT_ASSET", id, ip);
    }
    public List<AssetMaintenanceReport> repairHistory(Long id) {
        return repairs.findByAssetAssetIdOrderBySubmittedAtDesc(id);
    }
    public List<AssetMaintenanceReport> myReports(String actor) {
        return repairs.findBySubmittedByOrderBySubmittedAtDesc(actor);
    }
    private String requiredNotes(String notes) {
        if (notes == null || notes.isBlank()) throw new IllegalArgumentException("Enter repair or review notes.");
        if (notes.length() > 2000) throw new IllegalArgumentException("Notes must be 2,000 characters or fewer.");
        return notes.trim();
    }
    private void assetAlert(UserAccount recipient, String message, String url) {
        var notification = new Notification();
        notification.setUser(recipient); notification.setNotificationType(NotificationType.SYSTEM);
        notification.setMessage(message); notification.setActionUrl(url);
        notifications.save(notification);
    }
    @Transactional
    public void completeMaintenance(Long id, String actor, String notes, String ip) {
        var user = users.findByUniversityId(actor).orElseThrow(() -> new AccessDeniedException("Account was not found."));
        boolean staff = roles.findByUserUserIdAndActiveTrue(user.getUserId()).stream().anyMatch(r ->
                "Help Desk Support Staff".equals(r.getRole().getRoleName()) || "Department Support Team Member".equals(r.getRole().getRoleName()));
        if (!staff || user.getAccountStatus() != AccountStatus.ACTIVE) throw new AccessDeniedException("Support staff access is required.");
        var asset = assets.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Asset was not found."));
        if (asset.getAssignedUser() == null || !asset.getAssignedUser().getUserId().equals(user.getUserId()))
            throw new AccessDeniedException("This equipment is not assigned to you.");
        if (asset.getCondition() != AssetCondition.UNDER_MAINTENANCE && asset.getCondition() != AssetCondition.OUT_OF_SERVICE)
            throw new IllegalArgumentException("Only equipment under maintenance or out of service can be reported as repaired.");
        if (repairs.existsByAssetAssetIdAndStatus(id, AssetMaintenanceReport.Status.PENDING))
            throw new IllegalArgumentException("A completion report is already awaiting administrator review.");
        var report = new AssetMaintenanceReport(); report.setAsset(asset); report.setSubmittedBy(actor);
        report.setRepairNotes(requiredNotes(notes)); report.setSubmittedAt(java.time.LocalDateTime.now());
        repairs.saveAndFlush(report);
        roles.findByRoleRoleNameAndActiveTrue("System Administrator").stream().map(UserRole::getUser)
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE).distinct()
                .forEach(u -> assetAlert(u, actor + " reported maintenance complete for " + asset.getAssetTag() + ". Review the repair report.", "/admin/assets/" + id));
        logs.log(user, "ASSET_REPAIR_SUBMITTED", "IT_ASSET", id, ip);
    }
    @Transactional
    public void reviewMaintenance(Long assetId, Long reportId, String actor, boolean approved, String notes, String ip) {
        var admin = administrator(actor);
        var asset = assets.findForUpdate(assetId).orElseThrow(() -> new ResourceNotFoundException("Asset was not found."));
        var report = repairs.findById(reportId).orElseThrow(() -> new ResourceNotFoundException("Repair report was not found."));
        if (!report.getAsset().getAssetId().equals(assetId)) throw new IllegalArgumentException("This report belongs to another asset.");
        if (report.getStatus() != AssetMaintenanceReport.Status.PENDING) throw new IllegalArgumentException("This report has already been reviewed.");
        if (asset.getCondition() == AssetCondition.RETIRED) throw new IllegalArgumentException("Retired equipment cannot be approved for use. Update its condition before reviewing.");
        report.setReviewNotes(requiredNotes(notes)); report.setReviewedBy(actor); report.setReviewedAt(java.time.LocalDateTime.now());
        report.setStatus(approved ? AssetMaintenanceReport.Status.APPROVED : AssetMaintenanceReport.Status.MORE_WORK_REQUIRED);
        asset.setCondition(approved ? AssetCondition.WORKING : AssetCondition.UNDER_MAINTENANCE);
        repairs.saveAndFlush(report); assets.saveAndFlush(asset);
        users.findByUniversityId(report.getSubmittedBy()).ifPresent(u -> assetAlert(u,
                "Maintenance for " + asset.getAssetTag() + (approved ? " was approved." : " requires further work.") + " View your repair reports for administrator feedback.", "/my-assets"));
        logs.log(admin, approved ? "ASSET_REPAIR_APPROVED" : "ASSET_REPAIR_RETURNED", "IT_ASSET", assetId, ip);
    }
    public long count() { return assets.count(); }
    public Page<ItAsset> assignedTo(String universityId, int page) {
        var user = users.findByUniversityId(universityId)
                .orElseThrow(() -> new AccessDeniedException("Account was not found."));
        Specification<ItAsset> owned = (root, criteria, cb) ->
                cb.equal(root.get("assignedUser").get("userId"), user.getUserId());
        var result = assets.findAll(owned, PageRequest.of(Math.max(0, Math.min(page, 100000)), 12, Sort.by("assetTag")));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages())
            return assets.findAll(owned, PageRequest.of(result.getTotalPages() - 1, 12, Sort.by("assetTag")));
        return result;
    }
    public long count(AssetCondition condition) { return assets.count((root, criteria, cb) -> cb.equal(root.get("condition"), condition)); }
    public Page<Ticket> history(Long id, int page) {
        get(id);
        return tickets.findByAssetAssetIdOrderByCreatedDateDesc(id, PageRequest.of(Math.max(0, Math.min(page, 100000)), 10));
    }
    // Students receive shared equipment and their own assigned equipment, without account details.
    public List<AssetOption> selectable(String universityId) {
        var user = users.findByUniversityId(universityId).orElseThrow();
        Specification<ItAsset> allowed = (root, criteria, cb) -> cb.and(cb.notEqual(root.get("condition"), AssetCondition.RETIRED),
                cb.or(cb.isNull(root.get("assignedUser")), cb.equal(root.get("assignedUser").get("userId"), user.getUserId())));
        return assets.findAll(allowed, Sort.by("assetTag")).stream()
                .map(a -> new AssetOption(a.getAssetId(), a.getAssetTag(), a.getAssetName(), a.getLocation())).toList();
    }
    @Transactional
    public ItAsset forTicket(Long id, String universityId) {
        var user = users.findByUniversityId(universityId).orElseThrow();
        var asset = assets.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("The selected asset is no longer available. Choose another asset or leave it blank."));
        if (asset.getCondition() == AssetCondition.RETIRED) throw new IllegalArgumentException("This equipment is retired. Choose another asset or leave it blank.");
        if (asset.getAssignedUser() != null && !asset.getAssignedUser().getUserId().equals(user.getUserId()))
            throw new AccessDeniedException("This asset is assigned to another user.");
        return asset;
    }
}
