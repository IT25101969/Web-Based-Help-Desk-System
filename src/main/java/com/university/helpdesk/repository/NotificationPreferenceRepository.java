package com.university.helpdesk.repository;

import com.university.helpdesk.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserUserId(Long userId);

    boolean existsByUserUserId(Long userId);
}
