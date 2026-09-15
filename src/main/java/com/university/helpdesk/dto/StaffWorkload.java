package com.university.helpdesk.dto;

public record StaffWorkload(
        Long userId,
        String universityId,
        String displayName,
        long activeAssignments,
        boolean overloaded
) {
}
