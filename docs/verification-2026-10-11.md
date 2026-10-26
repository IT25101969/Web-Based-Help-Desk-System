# Website review — 11 October 2026

## Scope

Reviewed the application controllers, services, templates, security rules, existing regression coverage, and local MySQL relationships. Existing unrelated workspace changes were preserved.

The regression suite covers public pages; authentication, password recovery and role permissions; user creation/profile/status/roles/deactivation; student and staff tickets; category-to-department routing; department membership; assignment/reassignment/unassignment; status and priority updates; public/internal comments; attachment upload/download/deletion; feedback; notifications/preferences/email queues; FAQ creation/editing/publishing/archiving/deletion/search/suggestions/analytics; announcements and saved replies; deadlines/overdue reminders; reports/exports; monitoring/audit logs; private drafts; and asset inventory/ticket links/retirement.

## Fixes made during this review

- Moved account status, unlocking, role assignment/removal, and deactivation into `AdminAccountService`. Each action and its audit entry now share one transaction. Failed writes roll back account, role and student-subtype changes together. Session expiry happens after commit; rollback retains the existing session.
- Added a failure-injection test proving rollback when an audit write fails, plus real HTTP checks for committed versus rolled-back account changes and sessions.
- Added consistent 20,000-character browser/server limits for FAQ answers and ticket comments. Oversized input is rejected before records are created.
- Added browser checks against whitespace-only required text inputs throughout existing forms, retaining their existing formats and length limits.
- Styled date/time controls consistently and corrected Support Tools checkbox sizing/alignment. Checkbox inputs remain compact while text/date/select controls use consistent spacing.
- Expanded MySQL verification to check administrative create/update/delete operations and linked category, department, membership, FAQ, announcement, saved-reply and user records after flush/reload.
- Expanded the database integrity checker to include category/department, FAQ/category, role/account, department membership and notification links.
- Increased the test JVM code cache from 32 MB to 128 MB after the larger regression suite exhausted the previous setting.

## Verification

- Final Java regression run: 245 discovered; 240 passed; 5 MySQL tests intentionally skipped in the default H2 run; zero failures/errors. Maven package build succeeded.
- Private ticket draft JavaScript: 12 passed.
- Public HTTP routes and linked CSS/images: 16 passed.
- Real browser: corrected Support Tools controls, native required-text validation, desktop/mobile sizing, no horizontal overflow at 390 px, no broken images, no console errors on the inspected page.
- Local MySQL integrity checks: zero broken references, invalid feedback ratings, duplicate active assignments, duplicate feedback records, missing ticket subtypes or missing attachment files. Isolated insert/read/update/rollback check passed.

Dedicated MySQL workflow run: all 5 tests passed with no failures, errors or skips. Results are recorded in `full-site-mysql-review.log`. The integrity checker passed again afterward; existing record counts were unchanged (14 accounts, 20 tickets, 43 status history records, 8 assignments, 10 comments, 5 feedback records, 91 notifications, 1 private draft, 0 assets).

## Data and operating notes

The MySQL application tests run in rollback-only transactions and do not leave test accounts, tickets, assets or administrative content behind. Existing data is preserved. Identity sequences can advance even when inserted rows roll back.

Account deletion is deactivation so linked ticket/audit history remains valid. Assets with ticket history are retired instead of deleted. Category routing changes with active ticket assignments and membership removal with active department assignments remain blocked to preserve access.

External SMTP delivery was not exercised against a real mail provider. Email queue, preferences, retry and SMTP error handling are covered by automated tests. Provider delivery still depends on the configured SMTP account and service availability.

Restart the application in IntelliJ and hard-refresh the browser after applying these changes. Temporary review servers have been stopped; the user's normal server was not stopped.

These results describe the reviewed implementation and tested scenarios; they do not guarantee every possible runtime condition or external service behavior.
