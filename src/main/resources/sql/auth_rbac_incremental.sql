-- Authentication/RBAC incremental database update.
-- Safe to run against the existing university_helpdesk database.
-- The application currently uses spring.jpa.hibernate.ddl-auto=update,
-- so this script is also kept as an explicit SQL reference for the project.

USE university_helpdesk;

CREATE TABLE IF NOT EXISTS PASSWORD_RESET_TOKEN (
    Token_ID BIGINT NOT NULL AUTO_INCREMENT,
    User_ID BIGINT NOT NULL,
    Token_Hash VARCHAR(64) NOT NULL,
    Expires_At DATETIME(6) NOT NULL,
    Used BIT(1) NOT NULL DEFAULT b'0',
    Created_At DATETIME(6) NOT NULL,
    Used_At DATETIME(6) NULL,
    PRIMARY KEY (Token_ID),
    UNIQUE KEY uk_password_reset_token_hash (Token_Hash),
    KEY idx_password_reset_token_user (User_ID),
    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (User_ID)
        REFERENCES USER_ACCOUNT (User_ID)
        ON UPDATE RESTRICT
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
