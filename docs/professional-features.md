# Service workspace additions

Ticket submission and account creation validate input lengths before SQL writes. Incident details accept the same 150-character limit as the form, descriptions allow 20000 characters, severity allows 30 characters, and status notes allow 500 characters. Failed service-request validation preserves the requested-service text. Draft autosave remains paused if the initial draft lookup fails, protecting saved work. Staff attachment downloads use an escaped Content-Disposition header, fall back to binary content type for legacy metadata, and return 404 for a missing attachment record.

For local database verification, `scripts/ReviewDatabase.java` uses environment-provided credentials, reports aggregate row and integrity counts, checks attachment files, and verifies an isolated notice insert/read/update/rollback. The opt-in `MySqlWorkflowVerificationTests` uses MYSQL_REVIEW=true and DB_URL/DB_USERNAME/DB_PASSWORD, validates the existing schema, and rolls back its ticket lifecycle, replies, and feedback. Run `node --test scripts/TestTicketDraft.cjs` for draft failure/restore/autosave checks. None of these workflows needs credentials in source files.

The notification centre queries only the signed-in account's inbox, with unread and alert-type filters applied before pagination. Page sizes are bounded to 5–50 and updates sort by timestamp and ID so equal timestamps do not shuffle between pages. Out-of-range pages fall back to the last available page. Counts reflect the selected filters; the unread badge reflects the entire inbox. Overdue notifications also require a current support, manager, or administrator role, preventing an obsolete assignment from exposing ticket details after staff access is revoked.

The public entrance at `/` renders a campus-support landing page for anonymous visitors and redirects signed-in visitors through `/dashboard`. `/help` provides a separate public FAQ search, capped at 20 matches, containing published questions and answers only. Inactive or unknown requested categories return no guides. Active categories supply the service directory, grouped by department; department membership, user accounts, contact emails, and private ticket data are not included in the public view models. Featured guides are selected alphabetically and are labelled helpful guides rather than claimed to be popular. `/privacy` provides factual application information and directs visitors to their university’s official policy. Public GET access is limited to these three routes; the existing portal FAQ, tickets, notifications, and administration remain protected. Verify all published FAQ content and service descriptions are appropriate for public visitors before external deployment. Support hours and direct contact details are omitted until institution-approved information is available.

Service Setup also manages support-team department membership. Only active support staff and department managers can be added. Removing access is blocked while that account has an active assignment in the department. Membership updates revoke the target account’s existing sessions and are audited. New departments therefore have a complete setup path: create department, add eligible staff, map a category, then assign tickets.

Ticket lists now support database search (reference, subject, category), status filters, sorting, and pagination. Support queues also retain priority, department, unmapped-category, and assignment filters. All filtering runs before pagination. Student ownership and active staff department membership remain enforced by the query. Page sizes are bounded to 5–50, ordering includes the ticket ID for stable pages, and out-of-range pages return the last available page.

System administrators with MANAGE_SYSTEM can use **Service Setup** to create/edit departments and categories. Categories can be active or inactive; inactive categories remain in historical tickets but cannot receive new submissions. Category department changes affect existing tickets, so rerouting is blocked while any active assignment exists in that category. Setup mutations are transactional, validated, CSRF-protected, and recorded in activity logs. Existing staff memberships are required for a new department to have eligible assignees.

Resolution target indicators use elapsed hours from submission and the current priority. Defaults: critical 4 hours, high 24 hours, medium 72 hours, low 120 hours. Configure SLA_CRITICAL_HOURS, SLA_HIGH_HOURS, SLA_MEDIUM_HOURS, and SLA_LOW_HOURS in the deployment environment. Resolved/closed tickets display Completed. Open work displays On track, Due soon (within two hours), or Overdue. These are planning targets, not guaranteed completion times. Business-hour calendars, pause rules, and automated escalation are not implemented. Overdue alerts support in-app notifications and queued email; see smtp-setup.md.

The student ticket form suggests up to four published FAQ guides after a category selection or subject entry. Requests are debounced and stale requests cancelled. Guide links open the knowledge base in another tab, preserving the ticket form. Drafts are excluded and invalid/inactive category requests return no results. Guide text and file names render as text rather than HTML. When suggestions are unavailable, submitting the ticket remains possible.

University SSO/MFA is a separate deployment integration: it requires the university’s identity provider, protocol, client registration, and role mapping. SMTP delivery likewise requires an authorized mail configuration. These additions do not fabricate either integration.

Administrators with MANAGE_SYSTEM can manage campus notices and saved reply templates under **Support Tools**. Notices appear on public and portal pages only while published and within their scheduled window. Times use the application server's local time. Messages render as text. Saved replies can be disabled and are offered only in staff ticket composers; inserting one preserves existing text and requires a separate comment submission.

Student ticket drafts save to SQL after a one-second pause in editing. Each account has one private draft, with an explicit restore choice when returning to the form. Drafts may be incomplete and do not contain attachments. Network failures appear beside the form. Successful ticket creation clears only the submitting account's draft in the same transaction; unsuccessful submissions retain it.

Overdue follow-ups run every five minutes, beginning one minute after startup. Active assigned staff and the department manager receive notifications through their enabled in-app and email channels; administrators receive the fallback when no active manager is mapped. Account status, department access, and notification channel preferences are respected. Each recipient receives one reminder per ticket target, without changing ticket status. Email is queued and delivered only when MAIL_ENABLED=true. Configure SLA_ALERTS_ENABLED and SLA_ALERTS_DELAY to control the job. Changing a resolution target can generate a fresh reminder.

## Feature verification — 18 September 2026

The feature review reproduced and fixed five edge cases: BCrypt rejected passwords over
72 UTF-8 bytes during reset; direct reset-service calls accepted short passwords; oversized
attachment names reached SQL; Unicode download filenames were not encoded for HTTP headers;
and stale department memberships could receive new-ticket details after role revocation.
Password validation is now shared by account creation and reset, attachment names are
validated before storage, both download routes use UTF-8 filename encoding, and support
notifications check current account status, role, and department access.

The full Maven verification discovered 209 tests: 208 passed with zero failures/errors and
one opt-in MySQL test skipped. That MySQL lifecycle test was then run separately and passed.
Three JavaScript draft tests passed, and all three frontend scripts passed syntax checks.
Real HTTP tests exercised login, logout, CSRF, database-backed permissions, shared pages,
and the menu destinations for all six roles. Database checks found zero orphan rows,
duplicate active assignments, duplicate feedback, invalid ratings, missing ticket subtypes,
or missing attachment files; the isolated SQL write/read/update/rollback check passed.

The executable JAR built successfully, including in the original checkout after stopping
the previous process that held its target JAR open. The verified build was restarted on
port 8082 against the existing MySQL database; public/authentication pages and static assets
returned HTTP 200. The running JAR is the delivered copy outside target, so subsequent
project builds are not blocked by that process. Existing data and credentials were retained.
SMTP remains disabled pending privately supplied provider settings and inbox verification.
These checks cover the exercised workflows; they are not an exhaustive concurrency/load
or external university SSO/email-provider verification.

## Website recheck — 18 September 2026

A further recheck reproduced three issues and added regression coverage:
- Rapid submit clicks while waiting for draft autosave could trigger duplicate native submissions.
- Browser validation cancelling submission after an outstanding save left draft autosave paused.
- Missing/deleted notification navigation and mark-read requests raised an unhandled exception.

The draft form now ignores repeated submits while waiting, rechecks validity before native
submission, and resumes autosave if validation prevents submission. Notification lookup
uses the existing HTTP 404 exception. Browser checks verified public FAQ search, answer
expansion, sign-in navigation, and the forgot-password page. The five draft JavaScript
regression tests passed, and the rollback-only MySQL ticket lifecycle test passed.
Final Maven verification: 210 tests discovered, 209 passed, zero failures/errors, one
opt-in MySQL test skipped and passed separately. The executable JAR build succeeded.
