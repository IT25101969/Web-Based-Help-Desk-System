import java.sql.*;

/** Read-only verification. Supply DB_URL, DB_USERNAME and DB_PASSWORD as environment variables. */
class VerifyDatabase {
    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("DB_URL"),
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            connection.setReadOnly(true);
            print(connection, "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name");
            print(connection, "SELECT r.role_name, GROUP_CONCAT(p.permission_name ORDER BY p.permission_name) AS permissions FROM role r JOIN role_permission rp ON rp.role_id=r.role_id JOIN permission p ON p.permission_id=rp.permission_id WHERE rp.is_active=1 GROUP BY r.role_name");
            if (args.length == 0) return;
            long id = Long.parseLong(args[0]);
            print(connection, "SELECT ticket_id, reference_no, ticket_type, status, priority, resolved_date, closed_date FROM ticket WHERE ticket_id=" + id);
            for (String table : new String[]{"incident", "service_request", "attachment", "ticket_status_history", "ticket_assignment", "user_comment", "notification", "email_notification_queue", "feedback"}) {
                print(connection, "SELECT '" + table + "' AS entity, COUNT(*) AS rows_for_ticket FROM " + table + " WHERE ticket_id=" + id);
            }
            print(connection, "SELECT status, COUNT(*) AS assignments FROM ticket_assignment WHERE ticket_id=" + id + " GROUP BY status");
            print(connection, "SELECT comment_type, COUNT(*) AS comments FROM user_comment WHERE ticket_id=" + id + " GROUP BY comment_type");
            print(connection, "SELECT rating FROM feedback WHERE ticket_id=" + id);
            print(connection, "SELECT action FROM activity_log WHERE entity_type='TICKET' AND entity_id=" + id + " ORDER BY log_id");
            print(connection, "SELECT normalized_query, results_count FROM faq_search_log ORDER BY search_id DESC LIMIT 3");
        }
    }
    private static void print(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    if (i > 1) System.out.print(" | ");
                    System.out.print(metadata.getColumnLabel(i) + "=" + rows.getString(i));
                }
                System.out.println();
            }
        }
    }
}
