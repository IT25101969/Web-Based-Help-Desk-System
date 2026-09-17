# Integration verification — 17 September 2026

## Scope and branch

Work stayed on `full-site`, the integration branch renamed by the repository owner.
The original checkout contained 24 modified files and an untracked cookie file.
Existing functionality and edits were reviewed and retained, with security fixes
applied to the new mutation routes. `cookies.txt` is now ignored.

All six requested source branch tips are already ancestors of the integration
branch. No additional merge, force push, database reset, or change to `main`,
`develop`, or a member branch was needed.

## Build and integration fixes

- The initial Java compilation passed. The Maven wrapper required access to its
  local dependency cache outside the original restricted execution environment.
- Existing test suites were coupled to demo startup data and the local MySQL
  configuration. Tests now run against isolated H2 in MySQL mode, with explicit
  per-test accounts and reference data. Production demo seeding stays disabled.
- A timing-based ticket reference in report test fixtures could collide. It now
  uses a random identifier within the database column length.
- Removed the unused new bidirectional `UserAccount.userRoles` mapping and unused
  history timestamp setter. Role access continues through `UserRoleRepository`;
  history timestamps retain `@PrePersist` ownership.
- Preserved a single Surefire configuration and added a test-scoped H2 dependency.
- No conflict markers or TODO markers were found in application/test sources.

## Behavior and security changes

- Demo users, departments, categories, memberships, and FAQs require explicit
  `SEED_DEMO_DATA=true`; the default is false. Demo passwords come from
  `DEMO_PASSWORD`. No existing rows or passwords are automatically changed.
- Core role/permission initialization remains ordered: core roles first, optional
  dependent demo records second, RBAC grants third.
- Unassignment and comment deletion require the appropriate route permissions.
  Both enforce object-level department authorization. Unassignment locks the
  ticket and records an assignment end time. Status changes also lock the ticket.
- Report aliases require both the correct role and `VIEW_REPORTS`.
- Student deletion routes require `VIEW_OWN_TICKETS`. Ownership denials in student
  and notification mutation controllers propagate as HTTP 403.
- Attachment deletion checks that the stored path stays inside the upload root.
- Ticket creation and attachment storage share a transaction; invalid uploads
  cannot leave a successfully committed ticket behind.
- The service-request dashboard link now selects `SERVICE_REQUEST`. Category
  prefill accepts active categories only.
- The FAQ result summary now evaluates its category expression correctly instead
  of rendering the literal `selectedCategory.categoryName`.
- Submission, status, assignment, unassignment, priority and comment actions have
  activity logging. Reassignment clears stale resolution timestamps.
- Reset links are hidden by default; reset emails are queued for configured SMTP.
  The public base URL is configurable. Reset-token consumption uses a pessimistic
  lock, retains hash storage, expiry, invalidation and single-use behavior.
- CSRF, BCrypt, database-backed RBAC, session fixation protection and
  `spring.jpa.hibernate.ddl-auto=update` remain enabled.

## Dynamic content

Dashboard metrics, report summaries, chart inputs, ticket queues, notification
counts, FAQ results, workload values and selectable reference records are backed
by services/repositories. Existing zero-value template fallbacks are empty-state
defaults, not sample metrics. Static introductory text remains static.
Sample reference/FAQ content is confined to the explicitly enabled demo runner.

## Automated verification

`mvnw.cmd clean test`: **BUILD SUCCESS; 144 tests; 0 failures; 0 errors; 0 skipped**.
The clean test lifecycle also compiles application and test sources.
`mvnw.cmd package -DskipTests`: **BUILD SUCCESS** after the full test pass.

The suites cover ticket subtypes, validation, attachments and ownership; support
authorization, assignments and status handling; notification ownership,
preferences and queue retry behavior; report calculations and CSV escaping;
administration protections; FAQ search, visibility, analytics and access rules.
Added regressions cover login by email, failure tracking and lock duration,
hashed/single-use reset tokens, report alias permissions, mutation permissions,
CSRF, inactive category prefill, service-request selection, FAQ rendering and
cross-department unassignment. Context startup asserts six roles, nine
permissions and zero automatically seeded accounts.

## MySQL startup and browser workflow

Both Maven development startup and packaged startup reached
`Started HelpdeskApplication` against the existing MySQL database at port 3307,
with demo seeding and SMTP disabled. The packaged browser verification used
port 8082. No database was dropped or recreated.

Final packaged-build smoke checks also confirmed student login, the dashboard's
Request Service link preselecting Service Request, and the filtered FAQ summary
rendering `Showing 1 published solution in Password Reset`. Logout succeeded.

Browser ticket `HD-20260917-AB1552A4` (ID 1589), explicitly labeled as integration
verification, completed the following sequence:

1. Student logged in by university ID; later logged in by email.
2. FAQ search for `password` returned a published answer and logged the query.
3. FAQ category link prefilled the ticket form; student submitted an incident.
4. Support found it in the IT Support queue and assigned an eligible staff member.
5. Status changed to `IN_PROGRESS`; public and internal comments were added.
6. Priority changed to `HIGH`; ticket was escalated and resolved.
7. Student saw the public reply and could not see the internal note.
8. Student submitted five-star feedback and marked the resolution alert as read.
9. Administrator closed the ticket; active assignment became `COMPLETED`.
10. Reports, CSV export, FAQ analytics, activity logs, user administration and
    monitoring rendered. Audit records included `REPORT_VIEWED` and
    `REPORT_EXPORTED`; FAQ analytics showed the `password` search.

A read-only JDBC query confirmed ticket 1589 has one incident subtype row, six
status-history rows, one completed assignment, two comments (one PUBLIC and one
INTERNAL), one feedback row, 12 notifications and 12 queued emails. Resolution
and closure timestamps are populated. The verification data was retained as
evidence; existing records were not deleted. This incident did not include an
attachment; attachment and service-request behavior also has automated coverage.

## Database tables

The existing MySQL schema contains all 24 mapped tables:

`USER_ACCOUNT`, `ROLE`, `PERMISSION`, `USER_ROLE`, `ROLE_PERMISSION`,
`PASSWORD_RESET_TOKEN`, `ACTIVITY_LOG`, `STUDENT`, `DEPARTMENT`, `USER_DEPARTMENT`,
`CATEGORY`, `TICKET`, `INCIDENT`, `SERVICE_REQUEST`, `ATTACHMENT`,
`TICKET_STATUS_HISTORY`, `TICKET_ASSIGNMENT`, `USER_COMMENT`, `FEEDBACK`,
`NOTIFICATION`, `NOTIFICATION_PREFERENCE`, `EMAIL_NOTIFICATION_QUEUE`, `FAQ`,
`FAQ_SEARCH_LOG`.

## Active RBAC matrix verified in MySQL

- Student: `SUBMIT_TICKET`, `VIEW_OWN_TICKETS`.
- Help Desk Support Staff: `VIEW_ALL_TICKETS`, `ASSIGN_TICKET`, `UPDATE_TICKET`.
- Department Support Team Member: `VIEW_ALL_TICKETS`, `UPDATE_TICKET`.
- Department Manager: `VIEW_ALL_TICKETS`, `ASSIGN_TICKET`, `UPDATE_TICKET`.
- System Administrator: `VIEW_ALL_TICKETS`, `ASSIGN_TICKET`, `UPDATE_TICKET`,
  `MANAGE_USERS`, `MANAGE_FAQ`, `VIEW_REPORTS`, `MANAGE_SYSTEM`.
- University Management: `VIEW_REPORTS`; no administrative mutations.

Role/permission grants are read from the database, not inferred from usernames.
Support access additionally depends on active department membership.

## Remaining operational limitations

- Live SMTP delivery was not exercised because mail was intentionally disabled.
  Configure SMTP and `APP_PUBLIC_URL` before relying on email/password reset delivery.
- H2 tests do not replace a MySQL concurrency/load test. The browser workflow and
  JDBC checks verify real MySQL persistence for the exercised paths, not every
  possible CRUD permutation or concurrent administrator operation.
- A fresh production database requires an operator-provisioned initial admin and
  real department/category data. There is intentionally no default privileged
  production password or automatic demo content.
- This was local integration verification, not a deployment, penetration test,
  accessibility certification, or load benchmark.

## Files

Created: test database configuration, reusable test account fixtures, integration
regression tests, read-only `scripts/VerifyDatabase.java`, and this report.

Modified: Maven dependencies; README and ignore rules; data/security configuration;
student/notification/reset controllers; attachment, comment, feedback, reset,
assignment and ticket services; token repository; FAQ template; existing test
fixtures. The final commit also includes the pre-existing reviewed changes to
admin, support and notification CRUD controllers/repositories and their templates.
The commit's file list is the authoritative complete inventory.
