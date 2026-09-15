-- FAQ and End-User Guidance incremental database update.
-- Safe to run against the existing university_helpdesk database.
-- The application uses spring.jpa.hibernate.ddl-auto=update,
-- and this script provides explicit idempotent DDL reference.

USE university_helpdesk;

CREATE TABLE IF NOT EXISTS FAQ_SEARCH_LOG (
    Search_ID BIGINT NOT NULL AUTO_INCREMENT,
    User_ID BIGINT NULL,
    Query VARCHAR(200) NULL,
    Normalized_Query VARCHAR(200) NULL,
    Category_ID BIGINT NULL,
    Results_Count INT NOT NULL DEFAULT 0,
    Searched_At DATETIME(6) NOT NULL,
    PRIMARY KEY (Search_ID),
    KEY idx_faq_search_log_searched_at (Searched_At),
    KEY idx_faq_search_log_query (Query),
    KEY idx_faq_search_log_norm_query (Normalized_Query),
    KEY idx_faq_search_log_category (Category_ID),
    CONSTRAINT fk_faq_search_log_user
        FOREIGN KEY (User_ID)
        REFERENCES USER_ACCOUNT (User_ID)
        ON UPDATE RESTRICT
        ON DELETE SET NULL,
    CONSTRAINT fk_faq_search_log_category
        FOREIGN KEY (Category_ID)
        REFERENCES CATEGORY (Category_ID)
        ON UPDATE RESTRICT
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
