package com.university.helpdesk.service;

import com.university.helpdesk.entity.*;
import com.university.helpdesk.repository.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class TicketWorkspaceService {
    private final TicketRepository tickets;
    private final UserAccountRepository accounts;
    private final UserDepartmentRepository memberships;

    public TicketWorkspaceService(TicketRepository tickets, UserAccountRepository accounts, UserDepartmentRepository memberships) {
        this.tickets = tickets; this.accounts = accounts; this.memberships = memberships;
    }

    public Page<Ticket> search(String universityId, String scope, String query, TicketStatus status,
            TicketPriority priority, Long departmentId, boolean unmappedOnly, String assignmentState,
            String order, int page, int size) {
        UserAccount account = accounts.findByUniversityId(universityId).orElseThrow();
        List<Long> departments = "support".equals(scope) ? memberships.findByUserUserIdAndActiveTrue(account.getUserId())
                .stream().map(m -> m.getDepartment().getDepartmentId()).distinct().toList() : List.of();
        String term = normalizeQuery(query).toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Specification<Ticket> filter = (root, criteria, cb) -> {
            var category = root.join("category", JoinType.LEFT);
            var department = category.join("department", JoinType.LEFT);
            List<Predicate> conditions = new ArrayList<>();
            if ("student".equals(scope)) conditions.add(cb.equal(root.get("student").get("userId"), account.getUserId()));
            else if ("support".equals(scope)) conditions.add(departments.isEmpty() ? cb.disjunction() : department.get("departmentId").in(departments));
            else if (!"admin".equals(scope)) throw new IllegalArgumentException("Unknown ticket scope.");
            if (departmentId != null) conditions.add(cb.equal(department.get("departmentId"), departmentId));
            if (unmappedOnly) conditions.add(cb.isNull(department.get("departmentId")));
            if (status != null) conditions.add(cb.equal(root.get("status"), status));
            if (priority != null) conditions.add(cb.equal(root.get("priority"), priority));
            if (!term.isEmpty()) {
                String pattern = "%" + term + "%";
                conditions.add(cb.or(cb.like(cb.lower(root.get("referenceNo")), pattern, '\\'),
                        cb.like(cb.lower(root.get("subject")), pattern, '\\'),
                        cb.like(cb.lower(category.get("categoryName")), pattern, '\\')));
            }
            if ("ASSIGNED".equals(assignmentState) || "UNASSIGNED".equals(assignmentState)) {
                var sub = criteria.subquery(Long.class);
                var assignment = sub.from(TicketAssignment.class);
                sub.select(assignment.get("assignmentId")).where(cb.equal(assignment.get("ticket"), root),
                        cb.equal(assignment.get("status"), AssignmentStatus.ACTIVE));
                conditions.add("ASSIGNED".equals(assignmentState) ? cb.exists(sub) : cb.not(cb.exists(sub)));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        int boundedSize = Math.max(5, Math.min(size, 50));
        Sort sorting = switch (normalizeOrder(order)) {
            case "oldest" -> Sort.by("createdDate").ascending().and(Sort.by("ticketId"));
            case "subject" -> Sort.by("subject").ascending().and(Sort.by("ticketId"));
            default -> Sort.by("createdDate").descending().and(Sort.by("ticketId").descending());
        };
        Page<Ticket> result = tickets.findAll(filter, PageRequest.of(Math.max(0, Math.min(page, 100000)), boundedSize, sorting));
        if (result.getTotalElements() == 0) return Page.empty(PageRequest.of(0, boundedSize, sorting));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            result = tickets.findAll(filter, PageRequest.of(result.getTotalPages() - 1, boundedSize, sorting));
        }
        return result;
    }

    public static String normalizeQuery(String query) {
        String value = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        return value.substring(0, Math.min(value.length(), 200));
    }
    public static String normalizeOrder(String order) {
        return "oldest".equals(order) || "subject".equals(order) ? order : "newest";
    }
}
