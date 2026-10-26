package com.university.helpdesk.repository;

import com.university.helpdesk.entity.ItAsset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ItAssetRepository extends JpaRepository<ItAsset, Long>, JpaSpecificationExecutor<ItAsset> {
    @Override @EntityGraph(attributePaths = {"department", "assignedUser"})
    org.springframework.data.domain.Page<ItAsset> findAll(org.springframework.data.jpa.domain.Specification<ItAsset> filter,
            org.springframework.data.domain.Pageable pageable);
    @Override @EntityGraph(attributePaths = {"department", "assignedUser"})
    Optional<ItAsset> findById(Long id);
    boolean existsByAssetTagIgnoreCaseAndAssetIdNot(String tag, Long id);
    boolean existsBySerialNumberIgnoreCaseAndAssetIdNot(String serial, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from ItAsset a where a.assetId = :id")
    Optional<ItAsset> findForUpdate(@Param("id") Long id);
}
