-- Notification and Email Queue incremental database update.
-- Safe to run against existing university_helpdesk database.
-- The application uses spring.jpa.hibernate.ddl-auto=update,
-- so this script is also maintained as an explicit SQL reference.

USE university_helpdesk;

CREATE TABLE IF NOT EXISTS NOTIFICATION_PREFERENCE (
    User_ID BIGINT NOT NULL,
    In_App_Enabled BIT(1) NOT NULL DEFAULT b'1',
    Email_Enabled BIT(1) NOT NULL DEFAULT b'1',
    Created_At DATETIME(6) NOT NULL,
    Updated_At DATETIME(6) NOT NULL,
    PRIMARY KEY (User_ID),
    CONSTRAINT fk_notification_preference_user
        FOREIGN KEY (User_ID)
        REFERENCES USER_ACCOUNT (User_ID)
        ON UPDATE RESTRICT
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS EMAIL_NOTIFICATION_QUEUE (
    Queue_ID BIGINT NOT NULL AUTO_INCREMENT,
    User_ID BIGINT NOT NULL,
    Ticket_ID BIGINT NULL,
    Notification_ID BIGINT NULL,
    Recipient_Email VARCHAR(150) NOT NULL,
    Subject VARCHAR(200) NOT NULL,
    Message TEXT NOT NULL,
    Status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    Attempt_Count INT NOT NULL DEFAULT 0,
    Next_Retry_At DATETIME(6) NULL,
    Last_Error VARCHAR(1000) NULL,
    Created_At DATETIME(6) NOT NULL,
    Sent_At DATETIME(6) NULL,
    Updated_At DATETIME(6) NULL,
    PRIMARY KEY (Queue_ID),
    KEY idx_email_queue_status (Status),
    KEY idx_email_queue_next_retry (Next_Retry_At),
    KEY idx_email_queue_user (User_ID),
    KEY idx_email_queue_ticket (Ticket_ID),
    KEY idx_email_queue_notification (Notification_ID),
    CONSTRAINT fk_email_queue_user
        FOREIGN KEY (User_ID)
        REFERENCES USER_ACCOUNT (User_ID)
        ON UPDATE RESTRICT
        ON DELETE CASCADE,
    CONSTRAINT fk_email_queue_ticket
        FOREIGN KEY (Ticket_ID)
        REFERENCES TICKET (Ticket_ID)
        ON UPDATE RESTRICT
        ON DELETE SET NULL,
    CONSTRAINT fk_email_queue_notification
        FOREIGN KEY (Notification_ID)
        REFERENCES NOTIFICATION (Notification_ID)
        ON UPDATE RESTRICT
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
