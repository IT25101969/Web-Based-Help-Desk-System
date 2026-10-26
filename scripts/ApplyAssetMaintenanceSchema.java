import java.sql.*;
/** Additive migration for maintenance records. Does not change existing equipment or users. */
class ApplyAssetMaintenanceSchema {
    public static void main(String[] args) throws Exception {
        try (var c = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            if (!"university_helpdesk".equals(c.getCatalog())) throw new IllegalStateException("Unexpected database.");
            try (var s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS asset_maintenance_report (report_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, asset_id BIGINT NOT NULL, submitted_by VARCHAR(50) NOT NULL, repair_notes VARCHAR(2000) NOT NULL, submitted_at DATETIME(6) NOT NULL, status ENUM('PENDING','APPROVED','MORE_WORK_REQUIRED') NOT NULL, reviewed_by VARCHAR(50), reviewed_at DATETIME(6), review_notes VARCHAR(2000), INDEX idx_asset_repair_history(asset_id, submitted_at), CONSTRAINT fk_asset_repair_asset FOREIGN KEY (asset_id) REFERENCES it_asset(asset_id))");
                try (var r = c.getMetaData().getColumns(c.getCatalog(), null, "notification", "action_url")) {
                    if (!r.next()) s.executeUpdate("ALTER TABLE notification ADD COLUMN action_url VARCHAR(200) NULL");
                }
            }
            System.out.println("Maintenance table and notification action link are ready.");
        }
    }
}
