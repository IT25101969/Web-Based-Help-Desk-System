# Asset maintenance workflow

1. An administrator assigns equipment to a support staff account and sets its condition to Under maintenance or Out of service.
2. The assigned support member opens My Assets, chooses Mark Maintenance Complete, and enters repair notes (required, maximum 2,000 characters).
3. A pending repair report is saved. Active administrators receive an in-app notification linked to the asset detail page. The asset condition remains unchanged until review.
4. The administrator reviews the repair notes and related ticket history on the asset page. Review notes are required. Approve & mark working changes the condition to Working; Request further work changes it to Under maintenance.
5. The submitting staff member receives an in-app notification and can read the decision and feedback in My repair reports. After further work is requested, a new completion report may be submitted. Previous reports remain in the history.

Only the assigned active support member can submit. Only an administrator with MANAGE_SYSTEM can review. Requests require CSRF protection. Duplicate pending reports and repeated decisions are rejected, with asset-row locking serializing simultaneous submissions and reviews. Notes, decisions, condition changes, notifications and activity logs participate in the same transaction. Equipment with repair history must be retired rather than deleted.

## Database

ASSET_MAINTENANCE_REPORT stores the asset relationship, repair notes, submitter university ID, submission time, status, reviewer university ID, review notes and review time. Submitter/reviewer IDs are historical snapshots. NOTIFICATION.action_url stores the internally generated equipment link. Existing equipment and account data are preserved. Notifications use the in-app channel.

The additive migration is scripts/ApplyAssetMaintenanceSchema.java, using DB_URL, DB_USERNAME and DB_PASSWORD. It has been applied to the local university_helpdesk database. The application's Hibernate update configuration also creates the new schema on startup.

## Verification

Asset integration tests cover assignment privacy, rendered forms, submission, further-work feedback, resubmission, approval, notifications, validation, authorization, CSRF and history protection. Dedicated rollback-only MySQL tests verify actual report persistence, approval and asset condition changes without retaining test records. Database integrity checks cover repair relationships and duplicate pending reports.
