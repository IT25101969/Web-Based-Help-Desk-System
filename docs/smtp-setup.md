# SMTP and Gmail setup

Email is optional and disabled by default. In-app notifications keep their existing
preferences. Ticket submission, assignment/escalation, status changes, public replies,
and overdue alerts use the persistent email queue and respect email preferences.
Internal comments do not email students. Requested password resets use the queue
regardless of notification preferences. No SMTP call runs in a ticket or reset request.

## Gmail prerequisites

1. Choose the Gmail/Google Workspace mailbox that will send help desk messages.
2. Enable Google 2-Step Verification and create an App Password named University Help Desk
   at <https://myaccount.google.com/apppasswords>.
3. Use that 16-character App Password for `MAIL_PASSWORD`, never your normal Google password.
   Remove display spaces when entering it. Do not send credentials in chat or commit them.
4. Some managed accounts, security-key-only setups, and Advanced Protection accounts do
   not offer App Passwords. Ask the account administrator for an approved SMTP service
   if unavailable. Changing your Google password revokes existing App Passwords.

References: [Google App Password help](https://support.google.com/accounts/answer/185833)
and [Google SMTP settings](https://support.google.com/mail/answer/7104828).

## Required environment

- `MAIL_ENABLED=true`: enables delivery; default `false`.
- `MAIL_HOST=smtp.gmail.com`.
- `MAIL_PORT=587`: default; authenticated STARTTLS.
- `MAIL_USERNAME`: full sending Gmail address.
- `MAIL_PASSWORD`: Google App Password supplied privately at runtime.
- `MAIL_FROM`: same Gmail address, or a verified send-as alias. Defaults to `MAIL_USERNAME`.
- `APP_PUBLIC_URL`: the actual base URL recipients can open, with HTTPS in deployment.
  Local-only testing may use `http://localhost:8080` on the same computer.
- `SHOW_RESET_LINK=false`: keep reset tokens out of the browser response.

`MAIL_SCHEDULER_ENABLED` defaults to `true`; `MAIL_SCHEDULER_DELAY` defaults to 10000 ms.
Authentication, STARTTLS enablement and STARTTLS requirement default to true. SSL server
identity verification is enabled. Connect/read/write timeouts each default to 5000 ms.
Protocol and authentication debug logging are disabled. Startup does not connect to SMTP.

Other providers can configure `MAIL_SMTP_AUTH`, `MAIL_STARTTLS_ENABLED`,
`MAIL_STARTTLS_REQUIRED`, `MAIL_SSL_ENABLED`, `MAIL_CONNECTION_TIMEOUT`,
`MAIL_READ_TIMEOUT`, and `MAIL_WRITE_TIMEOUT`. For implicit TLS on port 465, set
`MAIL_SSL_ENABLED=true` and both STARTTLS flags to `false`. Do not disable TLS for Gmail.

## Start locally without putting secrets in shell history

Run this in PowerShell in the project folder, with your normal database environment
already configured. The secure prompt is for the App Password, not your Google password.

```powershell
$env:MAIL_HOST = 'smtp.gmail.com'
$env:MAIL_PORT = '587'
$env:MAIL_USERNAME = Read-Host 'Sending Gmail address'
$env:MAIL_FROM = $env:MAIL_USERNAME
$smtpSecret = Read-Host 'Google App Password (not normal password)' -AsSecureString
$smtpCredential = [System.Net.NetworkCredential]::new('', $smtpSecret)
$env:MAIL_PASSWORD = $smtpCredential.Password.Replace(' ', '')
$env:MAIL_ENABLED = 'true'
$env:MAIL_SCHEDULER_ENABLED = 'true'
$env:SHOW_RESET_LINK = 'false'
$env:APP_PUBLIC_URL = 'http://localhost:8080'
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Remove-Item Env:MAIL_PASSWORD -ErrorAction SilentlyContinue
    $smtpCredential = $null
    $smtpSecret = $null
}
```

For deployment, inject the same values from your service's secret manager/environment.
Spring Boot does not automatically load a `.env` file. `.env` and `.env.*` local files
are ignored by Git, but environment injection avoids keeping plaintext secrets on disk.
Never print environment variables, enable mail protocol debugging, or share reset email
bodies. Queue bodies contain reset links; restrict database/backups to authorized operators.

## Delivery, retries and limitations

When `MAIL_ENABLED=false`, queue records remain PENDING without consuming attempts.
Enabling delivery also processes previously queued mail; review stale test records before
enabling Gmail. Old/replaced reset links remain invalid and users should request a fresh
reset. The queue currently retains message bodies; apply an appropriate database retention
policy, especially for reset emails.

SMTP failures do not undo ticket changes, in-app notifications, or reset requests.
Failures retry after 1 minute and then 5 minutes; the third failed attempt is FAILED.
Only fixed safe error descriptions are stored/logged, never SMTP exception text.
The administration monitoring page shows pending and failed email counts. SENT means
the SMTP server accepted the message, not proof of inbox delivery. FAILED records are
not automatically retried after configuration changes; trigger a fresh event/reset.

Use one application instance to process this queue. It does not have a distributed claim
mechanism; multiple workers or a crash after SMTP acceptance can cause duplicate delivery.

## Verification

Run `.\mvnw.cmd verify` for all automated tests and the executable JAR build.
Tests use H2, mocked failure cases, and a real JavaMail exchange with a loopback-only
SMTP test server. They require no credentials and send no external email. The opt-in
MySQL review test requires `MYSQL_REVIEW=true` and separately configured database access.

Final Gmail verification must be performed after supplying the runtime credentials:

1. Use a test account whose recipient mailbox you control and enable its email preference.
2. Submit a ticket, add a public staff reply, and change its status. Confirm both the
   in-app notifications and received emails, allowing at least one scheduler cycle.
3. Request a fresh password reset, open its email link, and confirm it can only be used
   once. Confirm the page itself does not expose the reset link.
4. Check spam folders and the queue status if mail does not arrive. For authentication
   failures, check the App Password and account policy; for connection failures, check
   host, port, TLS settings, outbound firewall access, and provider quotas.

Actual Gmail authentication and inbox delivery cannot be verified without the owner's
credentials and a controlled recipient mailbox; automated success does not establish them.

### Verified on 2026-09-18

The full test run completed with 203 discovered tests: 202 passed, zero failures/errors,
and one intentionally skipped opt-in MySQL test. This includes seven new SMTP/workflow
tests and updated overdue notification expectations. The local SMTP transport test passed.
Packaging in the original checkout encountered a Windows lock while renaming the existing
JAR. A separate copy of the same source successfully produced the executable Spring Boot
JAR with `mvnw.cmd package -DskipTests`; the full suite had already passed in the checkout.
No running application was stopped and no external email was sent.
