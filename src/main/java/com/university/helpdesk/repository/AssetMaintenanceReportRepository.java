package com.university.helpdesk.repository;

import com.university.helpdesk.entity.AssetMaintenanceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetMaintenanceReportRepository extends JpaRepository<AssetMaintenanceReport, Long> {
    boolean existsByAssetAssetId(Long assetId);
    boolean existsByAssetAssetIdAndStatus(Long assetId, AssetMaintenanceReport.Status status);
    List<AssetMaintenanceReport> findByAssetAssetIdOrderBySubmittedAtDesc(Long assetId);
    List<AssetMaintenanceReport> findBySubmittedByOrderBySubmittedAtDesc(String universityId);
}
