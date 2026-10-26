import java.sql.*;
import java.nio.file.*;
import java.util.UUID;

/** Integrity checks, rollback verification, and opt-in committed persistence verification. */
class ReviewDatabase {
    static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
    }
    static long count(Connection c, String sql) throws SQLException {
        try (var s=c.createStatement(); var r=s.executeQuery(sql)) {r.next();return r.getLong(1);}
    }
    static void check(Connection c, String label, String sql) throws SQLException {
        long violations=count(c,sql);System.out.println(label+"="+violations);
        if(violations!=0)throw new SQLException("Integrity check failed: "+label);
    }
    public static void main(String[] args) throws Exception {
        try(var c=connect()) {
            System.out.println("database="+c.getCatalog()+" | server="+c.getMetaData().getDatabaseProductVersion());
            for(String table:new String[]{"user_account","ticket","ticket_status_history","ticket_assignment","user_comment","feedback","notification","campus_announcement","saved_reply","ticket_draft","it_asset"})
                System.out.println(table+"_rows="+count(c,"SELECT COUNT(*) FROM "+table));
            check(c,"orphan_ticket_assets","SELECT COUNT(*) FROM ticket t LEFT JOIN it_asset a ON a.asset_id=t.asset_id WHERE t.asset_id IS NOT NULL AND a.asset_id IS NULL");
            check(c,"orphan_asset_repairs","SELECT COUNT(*) FROM asset_maintenance_report r LEFT JOIN it_asset a ON a.asset_id=r.asset_id WHERE a.asset_id IS NULL");
            check(c,"duplicate_pending_asset_repairs","SELECT COUNT(*) FROM (SELECT asset_id FROM asset_maintenance_report WHERE status='PENDING' GROUP BY asset_id HAVING COUNT(*)>1) x");
            check(c,"orphan_asset_departments","SELECT COUNT(*) FROM it_asset a LEFT JOIN department d ON d.department_id=a.department_id WHERE d.department_id IS NULL");
            check(c,"orphan_asset_assignments","SELECT COUNT(*) FROM it_asset a LEFT JOIN user_account u ON u.user_id=a.assigned_user_id WHERE a.assigned_user_id IS NOT NULL AND u.user_id IS NULL");
            check(c,"orphan_tickets","SELECT COUNT(*) FROM ticket t LEFT JOIN student s ON s.user_id=t.student_id LEFT JOIN category c ON c.category_id=t.category_id WHERE s.user_id IS NULL OR c.category_id IS NULL");
            check(c,"orphan_category_departments","SELECT COUNT(*) FROM category c LEFT JOIN department d ON d.department_id=c.department_id WHERE c.department_id IS NOT NULL AND d.department_id IS NULL");
            check(c,"orphan_faq_categories","SELECT COUNT(*) FROM faq f LEFT JOIN category c ON c.category_id=f.category_id WHERE f.category_id IS NOT NULL AND c.category_id IS NULL");
            check(c,"orphan_user_roles","SELECT COUNT(*) FROM user_role ur LEFT JOIN user_account u ON u.user_id=ur.user_id LEFT JOIN role r ON r.role_id=ur.role_id WHERE u.user_id IS NULL OR r.role_id IS NULL");
            check(c,"orphan_department_memberships","SELECT COUNT(*) FROM user_department ud LEFT JOIN user_account u ON u.user_id=ud.user_id LEFT JOIN department d ON d.department_id=ud.department_id WHERE u.user_id IS NULL OR d.department_id IS NULL");
            check(c,"orphan_notification_users","SELECT COUNT(*) FROM notification n LEFT JOIN user_account u ON u.user_id=n.user_id WHERE u.user_id IS NULL");
            check(c,"orphan_notification_tickets","SELECT COUNT(*) FROM notification n LEFT JOIN ticket t ON t.ticket_id=n.ticket_id WHERE n.ticket_id IS NOT NULL AND t.ticket_id IS NULL");
            for(String table:new String[]{"incident","service_request","ticket_status_history","ticket_assignment","user_comment","feedback","attachment"})
                check(c,"orphan_"+table,"SELECT COUNT(*) FROM "+table+" x LEFT JOIN ticket t ON t.ticket_id=x.ticket_id WHERE t.ticket_id IS NULL");
            check(c,"duplicate_active_assignments","SELECT COUNT(*) FROM (SELECT ticket_id FROM ticket_assignment WHERE status='ACTIVE' GROUP BY ticket_id HAVING COUNT(*)>1) x");
            check(c,"duplicate_feedback","SELECT COUNT(*) FROM (SELECT ticket_id FROM feedback GROUP BY ticket_id HAVING COUNT(*)>1) x");
            check(c,"invalid_feedback_ratings","SELECT COUNT(*) FROM feedback WHERE rating<1 OR rating>5");
            check(c,"orphan_drafts","SELECT COUNT(*) FROM ticket_draft d LEFT JOIN user_account u ON u.user_id=d.user_id WHERE u.user_id IS NULL");
            check(c,"missing_incident_subtypes","SELECT COUNT(*) FROM ticket t LEFT JOIN incident i ON i.ticket_id=t.ticket_id WHERE t.ticket_type='INCIDENT' AND i.ticket_id IS NULL");
            check(c,"missing_service_subtypes","SELECT COUNT(*) FROM ticket t LEFT JOIN service_request s ON s.ticket_id=t.ticket_id WHERE t.ticket_type='SERVICE_REQUEST' AND s.ticket_id IS NULL");
            long missing=0;
            Path uploadRoot=Paths.get(System.getenv().getOrDefault("UPLOAD_DIR","uploads")).toAbsolutePath().normalize();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT file_path FROM attachment")){while(r.next()){Path file=uploadRoot.resolve(r.getString(1)).normalize();if(!file.startsWith(uploadRoot)||!Files.isRegularFile(file))missing++;}}
            System.out.println("missing_attachment_files="+missing);
            if(missing!=0)throw new SQLException("Integrity check failed: missing_attachment_files");
            String marker="SQL review "+UUID.randomUUID(); long id;
            c.setAutoCommit(false);
            try {
                try(var p=c.prepareStatement("INSERT INTO campus_announcement(title,message,kind,published,starts_at) VALUES(?,?,?,false,NOW())",Statement.RETURN_GENERATED_KEYS)){
                    p.setString(1,marker);p.setString(2,"Temporary transaction verification");p.setString(3,"INFORMATION");p.executeUpdate();
                    try(var r=p.getGeneratedKeys()){r.next();id=r.getLong(1);}
                }
                if(count(c,"SELECT COUNT(*) FROM campus_announcement WHERE id="+id)!=1)throw new SQLException("Inserted row unreadable");
                try(var p=c.prepareStatement("UPDATE campus_announcement SET message=? WHERE id=?")){p.setString(1,"Updated transaction verification");p.setLong(2,id);if(p.executeUpdate()!=1)throw new SQLException("Update failed");}
                verifyMessage(c,id,"Updated transaction verification");
                c.rollback();
                try(var independent=connect()){if(count(independent,"SELECT COUNT(*) FROM campus_announcement WHERE id="+id)!=0)throw new SQLException("Rollback failed");}
                System.out.println("isolated_insert_read_update_rollback=PASS");
            } finally {c.rollback();}
        }
        if(java.util.Arrays.asList(args).contains("--verify-commit"))verifyCommittedPersistence();
    }
    static void verifyMessage(Connection c,long id,String expected)throws SQLException {
        try(var p=c.prepareStatement("SELECT message,published FROM campus_announcement WHERE id=?")) {
            p.setLong(1,id);
            try(var r=p.executeQuery()) {
                if(!r.next()||!expected.equals(r.getString(1))||r.getBoolean(2))throw new SQLException("Stored value verification failed");
            }
        }
    }
    static void verifyCommittedPersistence()throws SQLException {
        String marker="SQL committed review "+UUID.randomUUID();Long id=null;
        try {
            try(var c=connect();var p=c.prepareStatement("INSERT INTO campus_announcement(title,message,kind,published,starts_at) VALUES(?,?,?,false,NOW())",Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1,marker);p.setString(2,"Committed insert verification");p.setString(3,"INFORMATION");p.executeUpdate();
                try(var r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("No generated key");id=r.getLong(1);}
            }
            try(var c=connect()) {
                verifyMessage(c,id,"Committed insert verification");
                try(var p=c.prepareStatement("UPDATE campus_announcement SET message=? WHERE id=? AND title=?")) {
                    p.setString(1,"Committed update verification");p.setLong(2,id);p.setString(3,marker);
                    if(p.executeUpdate()!=1)throw new SQLException("Committed update failed");
                }
            }
            try(var c=connect()){verifyMessage(c,id,"Committed update verification");}
            System.out.println("committed_insert_update_independent_reload=PASS");
        } finally {
            if(id!=null) {
                try(var c=connect();var p=c.prepareStatement("DELETE FROM campus_announcement WHERE id=? AND title=? AND published=false")) {
                    p.setLong(1,id);p.setString(2,marker);
                    if(p.executeUpdate()!=1)throw new SQLException("Temporary verification row cleanup failed");
                }
                try(var c=connect()){if(count(c,"SELECT COUNT(*) FROM campus_announcement WHERE id="+id)!=0)throw new SQLException("Cleanup verification failed");}
                System.out.println("committed_verification_row_cleanup=PASS");
            }
        }
    }
}
