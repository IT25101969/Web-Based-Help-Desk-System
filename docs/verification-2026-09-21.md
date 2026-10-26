# Website and database verification — 21 September 2026

## Result and fix

The application builds and runs against the existing `university_helpdesk` MySQL database on port 3307. Verification found a public error-handling bug: `/help?categoryId=invalid` redirected an anonymous visitor to login instead of returning HTTP 400. Missing public image URLs had the same error-dispatch problem.

The security configuration now permits the servlet container's ERROR dispatch. Normal student and administrator requests still require authentication and their existing permissions. Error pages show public navigation and recovery links to anonymous visitors, authenticated navigation to signed-in users, and a main-content target for keyboard navigation.

A real-HTTP regression reproduced the original 302 response before the fix and verifies 400/404 responses, rendered HTML, anonymous navigation, and protected workspace redirects after the fix. A repeatable, read-only public smoke checker is available with `node scripts/VerifyPublicHttp.cjs`.

## Verification evidence

- Full Maven suite: 211 tests discovered, 210 passed, zero failures/errors, one opt-in MySQL test skipped. Log: `current-verification-tests.log`.
- Opt-in MySQL workflow: one passed, zero skipped, with Hibernate schema validation enabled. Log: `current-mysql-workflow.log`.
- JavaScript draft tests: all five passed using `node --test scripts/TestTicketDraft.cjs`.
- Packaged application build passed. Log: `current-verification-package.log`.
- Running application: all 16 public-route, authentication-redirect, error-response, and linked-asset checks passed. Log: `current-live-http.log`.
- Chrome: verified FAQ navigation, password search, answer expansion, page appearance, and the corrected anonymous error page.

The full suite includes real HTTP login/logout and menu rendering for all six roles against isolated H2 fixtures, as well as ticket, attachment, authorization, administration, report, notification, reset, and validation tests. Existing user passwords were not changed for verification.

## Database verification

The actual MySQL workflow inserted a ticket and attachment, added public/internal comments, moved the ticket through IN_PROGRESS, RESOLVED and CLOSED, and submitted feedback. It flushed SQL and cleared the persistence context before reloading and asserting the stored values, history, timestamps, feedback and attachment contents. These writes were rolled back; this is SQL persistence verification inside a transaction, not a new committed production ticket.

Separate database checks found zero orphaned ticket-related records, duplicate active assignments, duplicate feedback, invalid feedback ratings, missing ticket subtype rows, orphaned drafts, or missing attachment files. An isolated insert/read/update/rollback check also passed. Log: `current-database-verification.log`.

Before and after the rollback checks, the database retained 13 accounts, 20 tickets, 43 status-history rows, 8 assignments, 10 comments, 5 feedback rows, 91 notifications and 1 draft. No database reset or manual data repair was required.

## Limits and content note

Live external email delivery remains unverified because mail is disabled. SMTP behavior has automated coverage. This review does not establish that every possible input, concurrent request, browser, or load level is bug-free. Authenticated live-HTTP coverage used isolated test accounts; the MySQL workflow exercised application services directly.

The test FAQ entry titled `hffuf` was archived after the review, so it is no longer exposed on the public homepage or help page. Spring reports its existing open-in-view configuration warning; this is not a startup failure.
