package com.university.helpdesk.repository;

import com.university.helpdesk.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUniversityId(String universityId);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByUniversityIdOrEmail(
            String universityId,
            String email
    );

    boolean existsByUniversityId(String universityId);

    boolean existsByEmail(String email);

    long countByAccountStatus(com.university.helpdesk.entity.AccountStatus accountStatus);

    long countByFailedLoginAttemptsGreaterThanEqual(int attempts);

    long countByAccountStatusOrFailedLoginAttemptsGreaterThanEqual(
            com.university.helpdesk.entity.AccountStatus status,
            int attempts
    );
}