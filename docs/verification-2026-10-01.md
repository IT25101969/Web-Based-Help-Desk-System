# Website and database review — 1 October 2026

## Changes

- Draft requests now reject redirects, unexpected HTML and non-204 clear responses. An expired session cannot falsely report that a saved draft was cleared and enable replacement of existing work.
- Priority updates acquire the same ticket write lock used for status and assignment changes. This protects status values from concurrent priority edits. A regression test uses two independent transactions and checks the final persisted status and priority.
- `SERVER_PORT` now controls the application port as documented; the default remains 8080.
- The database checker verifies the updated message value, fails on missing attachment files, and supports `--verify-commit` to exercise committed persistence using an unpublished temporary announcement and independent connections. It deletes only its generated verification row and verifies cleanup.

## Verification

- Full Maven suite and package build: passed; 226 tests discovered, 225 passed, zero failures/errors, one opt-in MySQL test skipped in the standard run and run separately against MySQL.
- JavaScript draft suite: eight passed, including session expiration, successful clear, failed initial load, double submission and validation recovery.
- Actual MySQL schema validation and ticket lifecycle test: passed. Ticket, priority update, attachment contents, public/internal comments, status history, resolution/closure timestamps and feedback survived SQL flush and persistence-context clearing. The transaction rolled back.
- Actual MySQL JDBC checks: no orphan records, duplicate active assignments, duplicate feedback, invalid ratings, missing subtype rows, orphan drafts or missing attachment files. Insert/read/update/rollback passed.
- Committed MySQL insert and update: passed, with reads from independent connections. Temporary unpublished announcement cleanup passed.
- Live public HTTP checker: all 16 route, protected-redirect, error-response and asset checks passed.
- Browser: homepage appearance, FAQ navigation/search/expansion, mobile FAQ and mobile login verified. No horizontal overflow or broken images on the inspected mobile pages at 390px.

## Existing data and limits

Database checks retained 13 accounts, 20 tickets, 43 status-history rows, eight assignments, ten comments, five feedback rows, 91 notifications and one draft. Existing passwords were not changed. Committed verification can advance the announcement auto-increment sequence, even though its temporary row is removed.

Authenticated HTTP checks use isolated H2 accounts for all six roles. MySQL application-service verification uses the existing student and administrator accounts inside a rollback transaction. Live external SMTP delivery is unverified because delivery was disabled for this review. These checks do not prove absence of every possible browser, concurrency or load issue.

Evidence logs are ignored local files: `current-final-site-review.log`, `current-mysql-review.log`, `current-concurrency-review.log` and `current-database-review.log`.
