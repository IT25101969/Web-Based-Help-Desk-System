package com.university.helpdesk.repository;

import com.university.helpdesk.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndUsedFalse(String tokenHash);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from PasswordResetToken t where t.tokenHash = :hash and t.used = false")
    Optional<PasswordResetToken> findUnusedForUpdate(@org.springframework.data.repository.query.Param("hash") String hash);

    List<PasswordResetToken> findByUserUserIdAndUsedFalse(Long userId);
}
