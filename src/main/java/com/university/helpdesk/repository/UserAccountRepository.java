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

    boolean existsByUniversityIdIgnoreCaseAndUserIdNot(String universityId, Long userId);
    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.userId = :id")
    Optional<UserAccount> findForProfileUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    long countByAccountStatus(com.university.helpdesk.entity.AccountStatus accountStatus);

    long countByFailedLoginAttemptsGreaterThanEqual(int attempts);

    long countByAccountStatusOrFailedLoginAttemptsGreaterThanEqual(
            com.university.helpdesk.entity.AccountStatus status,
            int attempts
    );
}
