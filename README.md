# Web-Based Help Desk for University Students System

Group ID: 2026-Y2-S1-MTR-07

University Help Desk System built with Java 21, Spring Boot, Spring Security,
Thymeleaf, Spring Data JPA, and MySQL.

## Run locally

Use the existing `university_helpdesk` database. Startup uses Hibernate `update`;
it does not drop tables or delete existing records.

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/university_helpdesk'
$env:DB_USERNAME = 'root'
$credential = Get-Credential -UserName $env:DB_USERNAME -Message 'Local MySQL credentials'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
$env:SEED_DEMO_DATA = 'false'
$env:MAIL_ENABLED = 'false'
.\mvnw.cmd spring-boot:run
```

The default application URL is `http://localhost:8080`. `SERVER_PORT` overrides it.
Use a dedicated database account with appropriate privileges in a deployed environment.

## Data initialization

Normal startup initializes only the six roles, nine permissions, and their mappings.
Existing accounts and records are preserved. Demo users, departments, categories,
memberships, and FAQs are created only with `SEED_DEMO_DATA=true` and a non-empty
`DEMO_PASSWORD` supplied through the environment. Existing demo passwords are never
overwritten. Leave demo seeding disabled for normal operation.

For a completely empty production database, an operator must provision the first
administrator (with a BCrypt hash and active administrator role) and the real
department/category reference records. The app intentionally does not create a
default production administrator or a known password. Administrators can create
subsequent user accounts through the website.

## Email and password reset

SMTP is optional. Set `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
`MAIL_PASSWORD`, and `MAIL_FROM` through the environment to enable delivery.
`APP_PUBLIC_URL` must be the externally reachable base URL for password reset links.
Notifications and password reset emails use the persistent retry queue. Delivery
is disabled when `MAIL_ENABLED=false`; queued emails remain pending.

`SHOW_RESET_LINK` defaults to `false`. Enable it only for local development when
email is unavailable. Reset token records contain SHA-256 hashes, expire after
30 minutes, and are invalidated after use or replacement. As with any queued
email, a reset email body contains its delivery link; restrict database access.

## Verification

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd clean test
.\mvnw.cmd package -DskipTests
```

Tests use an isolated H2 database in MySQL compatibility mode and their own
fixtures, with mail and demo seeding disabled. They never use the local MySQL
database or the normal upload directory. Stop an application running from
`target/` before `clean` on Windows, because its JAR remains locked while running.

`scripts/VerifyDatabase.java` is a read-only JDBC verification helper. Run it with
the MySQL connector JAR on the classpath, the same database environment variables,
and an optional ticket ID. It prints schema names, active RBAC mappings, and
workflow persistence counts without exposing passwords or token contents.

See `docs/integration-verification.md` for the integration results and limitations.
