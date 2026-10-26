-- MySQL: back up first. DDL commits automatically; widening preserves existing text.
ALTER TABLE ticket MODIFY COLUMN description MEDIUMTEXT NOT NULL;
ALTER TABLE faq MODIFY COLUMN answer MEDIUMTEXT NOT NULL;
ALTER TABLE user_comment MODIFY COLUMN comment_text MEDIUMTEXT NOT NULL;
