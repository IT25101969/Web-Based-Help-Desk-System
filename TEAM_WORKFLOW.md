# Team Development Workflow

Project: Web-Based Help Desk for University Students System  
Group: 2026-Y2-S1-MTR-07

## Branch ownership

| Member | Student ID | Function | Branch |
|---|---|---|---|
| Jayathilaka H.M.S.K. | IT25101969 | Authentication & RBAC | `feature/it25101969-auth-rbac` |
| Dulmini N. V. | IT25101961 | Incident & Service Request Submission | `feature/it25101961-ticket-submission` |
| Dias D. M. C. P. | IT25101968 | Notifications & Ticket Status Alerts | `feature/it25101968-notifications` |
| Rathugamage R. G.D. | IT25101966 | Reports, Monitoring & Administration | `feature/it25101966-reports-admin` |
| Prabhath K.E.R.K. | IT25101959 | Ticket Handling for Support Teams | `feature/it25101959-ticket-handling` |
| Pavithran Y. | IT25101971 | FAQ & End-User Guidance | `feature/it25101971-faq-guidance` |

## Shared branches

- `main` — final stable release only
- `develop` — group integration branch
- `codex/full-project-integration` — Codex-assisted integration/testing branch

## Rules

1. Each member works only on their own feature branch for their assigned function.
2. Do not push feature work directly to `main`.
3. Do not overwrite another member's files without coordination.
4. Before starting work, update the feature branch from `develop`.
5. Commit small logical changes with clear messages.
6. Push frequently to the member's own branch.
7. When a feature is ready, open a Pull Request into `develop`.
8. Resolve merge conflicts before merging.
9. Run tests before creating a Pull Request.
10. Database schema changes must be coordinated because all modules share the same MySQL database.
11. Do not commit database passwords, secrets, `.idea/`, `target/`, uploads, or local environment files.
12. Use environment variables for database credentials.

## Start-of-day commands

Run these from the project folder:

```bash
git checkout develop
git pull origin develop
git checkout <your-feature-branch>
git merge develop
```

Example:

```bash
git checkout feature/it25101961-ticket-submission
git merge develop
```

## Save work

```bash
git status
git add .
git commit -m "Describe the feature completed"
git push
```

## Before Pull Request

Windows PowerShell:

```powershell
.\mvnw.cmd clean test
```

The build should end with:

```text
BUILD SUCCESS
```

## Database coordination

All members use the same logical schema: `university_helpdesk`.

Do not create one database per member.

If a member needs a new table/column:
1. coordinate the change,
2. add/update the JPA entity,
3. add a reproducible SQL/migration change,
4. test it,
5. include the database change in the member's Pull Request.

## Pull Request flow

```text
member feature branch
        ↓
Pull Request
        ↓
develop
        ↓
integration testing
        ↓
main
```
