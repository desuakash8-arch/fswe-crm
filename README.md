# FSWE CRM

React/Vite frontend and Spring Boot 4.1.2/MySQL backend.

## Run on Windows

Open two PowerShell terminals at `C:\Users\AKASH\fswe-company-role-updated`.

Terminal 1:
```powershell
npm run backend
```

Terminal 2:
```powershell
npm run dev
```

Open http://127.0.0.1:5173. The backend uses port 8085. Vite proxies `/api` to the backend.

Direct backend command (from `fswe/crmbackend`): `./mvnw.cmd spring-boot:run`.
Direct frontend command (from `fswe/crmfrontend`): `npm run dev`.
Do not start another copy while a server already owns its port. Stop a terminal's server with Ctrl+C.

## Configuration

The working local DB and SMTP credentials are preserved in `fswe/crmbackend/application-local.properties`, which is ignored by Git. The backend imports this file when started from its directory. Shell environment variables override these local values.

`fswe/crmbackend/.env.example` lists available settings. Spring Boot does not automatically load `.env` files. Set environment variables in PowerShell, for example:
```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_mysql_password"
npm run backend
```

Database: existing `crm_db` in MySQL. Flyway now applies versioned migrations, then Hibernate validates the schema. The initial migration can adopt the existing local schema without dropping tables. Do not change applied migrations; add a new migration for future schema changes. Keep `DB_DDL_AUTO=validate`.

Email is enabled on this machine through `application-local.properties`. Local test routing redirects emails to the configured test inbox and labels their subjects `[LOCAL TEST]`; employee records are unchanged. Set `app.mail.recipient-override=` in the local file and unset `MAIL_RECIPIENT_OVERRIDE` to restore delivery to assigned employees. New installations default to disabled: set `MAIL_ENABLED=true`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` to valid SMTP credentials and sender email, then restart the backend. Every new in-app notification writes a durable email job in the same transaction when mail is enabled. This covers customers, leads, conversion, all deal stages, interactions, tasks, follow-ups, reminders, companies, user accounts and CSV imports. A background worker sends committed jobs every 15 seconds. Failed jobs retry up to three times with increasing delays. Saving unchanged record details or marking a notification as read does not resend. Admins can inspect the queue and retry failed jobs in **System activity**; each deal has its own email history. `ACCEPTED` means SMTP acceptance, not inbox delivery. Later bounces still arrive in the sender inbox. SMTP cannot guarantee exactly-once delivery; retrying after an uncertain connection failure may send a duplicate.

`JWT_SECRET` is required, with no built-in fallback. A private key is configured in the ignored local file. Changing it invalidates previous sessions. Password changes, password resets and account deactivation also invalidate existing tokens. `SERVER_PORT` overrides the backend port; set `API_PROXY_TARGET` when using a different backend address. A deployed frontend may set `VITE_API_BASE_URL` at build time, or serve `/api` through its web server.

Public registration is disabled. Existing Admins/Managers create accounts through Users. Managers create Sales users for their own company. Sales users can use the assignment lookup endpoint without receiving user-management access. Records are scoped to Sales ownership and Manager company.

## Verify

From the project root:
```powershell
npm run build
npm run lint
npm run test:backend
npm run test:e2e
```

Backend tests use isolated in-memory H2 databases. Browser tests start a separate H2 backend on 8086 and Vite on 5174, seed test-only accounts and stop both servers afterward. They never use the local MySQL database or send real mail. Install browser test dependencies with `npm --prefix fswe/crmfrontend ci`; Windows uses installed Microsoft Edge. On Linux, run `npx playwright install --with-deps chromium` in the frontend folder and set `PLAYWRIGHT_CHANNEL=chromium`. Ports 8086 and 5174 must be free. `npm run verify` runs lint, build, backend tests and browser tests. The same checks are configured in `.github/workflows/verify.yml`.

## Notification emails across the CRM

All new in-app notifications go through `CrmNotifications`, which saves the notification and queues its email in the same transaction. SMTP runs asynchronously; an SMTP failure does not undo the saved CRM action. No database migration or historical notification replay is needed.

| Event | Recipient |
| --- | --- |
| Customer/lead/deal/task/interaction creation, meaningful edits and deletion | Assigned employee; acting user when unassigned |
| Lead conversion and resulting customer/opportunity creation | Assigned employee, with a notification for each resulting event |
| Assignment change | New assignee receives details; previous assignee receives a brief reassignment notice |
| All pipeline stage changes, including Won/Lost/reopening | Assigned employee; Lost includes the supplied reason |
| Follow-up creation and task reminders | Task assignee; includes source notes, due date and reminder reason |
| Company creation, update or deletion | Acting administrator |
| User creation/profile/role/password/activation changes | Affected user; passwords, hashes and reset tokens are excluded |
| User deletion | Acting administrator; deleted user's in-app inbox is removed transactionally |
| CSV import | Importing user receives record notifications and an import summary |
| Manually created notification | Selected permitted recipient |

Emails include the event, actor when applicable, record reference, changed values, and available reasons/interaction notes. Ordinary page views, searches, unchanged saves, CSV previews, and marking notifications as read do not create emails. Existing notification edits/deletions do not resend old messages. Password reset requests retain their dedicated reset-link email. Historical notifications are not resent when upgrading. Local test routing still overrides every recipient to the configured test inbox.

## Customer payments — Razorpay test mode

Open **Payments**, choose **Create invoice**, and select a won deal with a customer and an assigned employee. The full deal amount becomes the fixed INR invoice amount (minimum INR 1). One invoice is allowed per deal; the deal's amount, customer and stage are locked after invoicing. This demonstration uses the deal total as entered; it does not calculate GST or issue statutory tax invoices.

Creating an invoice automatically queues a payment-link email when SMTP, Razorpay test keys, and the customer's email address are configured. The invoice still saves if email is unavailable, and the page explains why its email was not queued. Existing invoices have an **Email payment link** button. It reuses the currently displayed active link; when no link is displayed, it generates and emails a replacement, invalidating older links. Requests for settled or processing invoices are blocked. Correct a missing/invalid customer email on Customers, then retry from the invoice.

The message includes the invoice reference, amount, expiry date, and a clickable checkout URL. Customers open it and click **Pay**; no CRM login is required. You can also use **Open payment page** or copy the link. Links expire in 30 days; **Replace payment link** invalidates the previous link. The invoice stores only the token hash, while the outgoing email body necessarily contains the full link. Email bodies are excluded from the email-history API. Anyone holding the link can view that invoice, so share it only with the intended customer.

Email links use `PUBLIC_APP_URL` (or `app.public-url`) from the backend configuration, never an address supplied by a browser request. The default `http://127.0.0.1:5173` works only on the laptop running the CRM. Customers using another device need a publicly reachable frontend URL whose `/api` routes reach the backend. Configure that URL before emailing links for remote use. Existing demo email routing is preserved: with `MAIL_RECIPIENT_OVERRIDE=desuakash8@gmail.com`, messages go to that inbox and identify the original demo customer address. Queue acceptance does not prove inbox delivery; SMTP acceptance/failures remain visible in email history.

Configure these values in the backend environment or the ignored `fswe/crmbackend/application-local.properties`, then restart the backend:

Do not put credentials in `src/test/resources/application.properties`: that file is only used by isolated automated tests and is not loaded by the running CRM. The application also accepts the equivalent Spring properties `razorpay.key.id`, `razorpay.key.secret`, and `razorpay.webhook.secret` in the private local file.

```properties
RAZORPAY_KEY_ID=rzp_test_yourActualKeyId
RAZORPAY_KEY_SECRET=yourActualTestKeySecret
RAZORPAY_WEBHOOK_SECRET=yourSeparateWebhookSecret
```

Use keys from Razorpay Dashboard **Test mode**. Live-mode keys are deliberately rejected. Configure automatic capture in the Razorpay Dashboard. Only a server-confirmed captured payment marks an invoice paid; an authorized payment remains Processing. Neither the client-reported amount nor a successful browser popup alone is accepted as proof of payment. The backend verifies the checkout HMAC signature and fetches the payment from Razorpay to match its order, amount, currency and captured state.

**Check payment status** on either the customer page or staff invoice page reconciles with Razorpay if the browser callback was interrupted. Failed attempts remain in payment history and can be retried using the same order. Duplicate confirmations do not repeat receipts. An uncertain order-creation request is recovered by its unique receipt rather than automatically generating another order. Payment statuses and already-recorded refunds can be refreshed, but initiating refunds and partial payments are outside this version.

For automatic updates when the customer closes the browser, configure a Razorpay **test-mode** webhook pointing to your public HTTPS backend URL plus `/api/public/payments/webhook`, with the same `RAZORPAY_WEBHOOK_SECRET`. Subscribe to `payment.captured`, `payment.failed`, `payment.authorized`, and `order.paid`. Localhost cannot receive Razorpay's external webhooks without a public HTTPS tunnel; manual status checks work locally. Webhooks verify the raw-body signature, fetch the payment from Razorpay, and safely tolerate duplicate/out-of-order events. No tunnel is started automatically.

Payment confirmations notify the assigned employee and queue a customer receipt. Existing local mail redirection still sends all emails to the configured test inbox. Every checkout and receipt says **test mode**: no real money is transferred. Card numbers and bank credentials are entered only in Razorpay's checkout and are not stored by this CRM.

Automated payment tests mock Razorpay responses. Browser tests use an explicitly imported gateway fixture in `src/test` and a mocked checkout script; neither is packaged in the application. They verify the CRM's full invoice/verification/receipt workflow but do not prove that your merchant credentials or live Razorpay test checkout are configured. Complete one actual Razorpay test payment after configuring your own keys.

Integration references: [Standard Checkout](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/), [webhook verification](https://razorpay.com/docs/webhooks/validate-test/).

## Deal health

In **Deals** or the **Pipeline**, select **Why this status?** beside a health badge. The detail page explains the status, shows the last interaction and next action, and offers shortcuts to deal tasks, interactions, and the closing-date editor. It uses the existing page theme and supports dark mode and mobile screens.

- **On track:** recent contact, an upcoming action, and a current closing date, with no overdue work.
- **Needs attention:** no recorded contact, no contact for at least 7 full days, no upcoming action, a missing closing date, or work overdue by less than 3 days.
- **At risk:** a past closing date or a task/follow-up overdue by at least 3 days. The most severe applicable rule determines the status.
- **Closed:** Won and Lost deals are excluded from open-deal checks.

Health evaluates only activities explicitly linked to that deal and visible to the signed-in user. In task and interaction editors, select **Related deal**, or create the activity directly on its health page. Existing customer activity stays intact and unlinked until you select its deal; activity for another opportunity with the same customer is not counted. Generated follow-up tasks inherit their interaction's deal, and their status/due date becomes authoritative. Completed/cancelled tasks do not keep a follow-up overdue. Only the latest completed interaction's raw follow-up is used when it has no generated task. Tasks due today are not overdue; future-dated interactions are not treated as completed contact.

Health refreshes after activity changes and every minute while the page is open. It is a transparent workflow assessment, not a win probability, and it never moves pipeline stages automatically. Server-local dates determine overdue checks. Optional positive-integer backend properties `app.deal-health.inactive-days` (default 7) and `app.deal-health.overdue-risk-days` (default 3) adjust the thresholds.

Browser test ports can be changed when the defaults are occupied: set `CRM_E2E_BACKEND_PORT` and `CRM_E2E_FRONTEND_PORT` to distinct unused ports before running `npm run test:e2e` from the project root.

## Completed CRM workflows

- **Customers:** open a name to view contact information, linked source leads, interactions, deals and tasks. Each activity list has server-side search, status filters, sorting and pagination.
- **Leads:** edit status, assign an employee, or convert to a linked customer with an optional opportunity. A converted lead cannot be converted again or manually reset to another status. Existing historical conversions are not guessed from matching names.
- **Deals:** use the list or Pipeline board. WON/LOST records an actual closing date; LOST requires a reason. Reopening clears the closing date. History records the actor and stage changes. Legacy closed deals with unknown closing dates are excluded from dated reports rather than assigned fabricated dates.
- **Tasks:** follow-ups link to the originating interaction and customer. Two interactions with the same customer/title/date can each have a follow-up, but the same interaction cannot create two tasks. In-app reminders run every minute for tomorrow, today and overdue work, once per task/date/category. Completed/cancelled tasks and inactive owners are skipped. Every new reminder also queues email when `MAIL_ENABLED=true`; the old separate reminder-email switch is no longer used.
- **Reports:** open pipeline excludes WON/LOST. Date filters apply to actual deal closing dates and to the creation cohort for lead conversion. Employee performance and monthly won values respect the same role visibility as the records.
- **Import/export:** CSV preview validates rows before writing. Imports support up to 500 rows / 1 MB, skip duplicate emails among visible records, and assign new records to the importing user. Exports support up to 10,000 accessible records and escape spreadsheet formulas. Existing records are not merged automatically.
- **Accounts:** Admins/Managers can deactivate and reactivate permitted employees. Account settings changes passwords. Forgot password emails a single-use, 30-minute reset link; tokens are hashed in the reset table. In local test mode these emails also go to the configured test inbox.
- **System activity:** Admin-only audit and email queue. Audit entries omit passwords, tokens and request bodies; deal history additionally records stage details.

## Backup and restore

From the project root, `npm run backup:db` writes a timestamped SQL backup under the ignored `backups/` directory. It uses `mysqldump`, reading the ignored local configuration without printing credentials. Set `MYSQL_BIN` if MySQL's tools are installed elsewhere. Protect backups because they contain customer data and password hashes.

To test recovery into a **new** database:
```powershell
node scripts/database.mjs restore --local --database crm_restore_check --input backups/your-backup.sql
node scripts/database.mjs counts --local --database crm_restore_check
```
The restore command refuses to overwrite an existing database. Start a separate backend with `--spring.datasource.url=jdbc:mysql://localhost:3306/crm_restore_check --server.port=8087 --app.mail.enabled=false --app.jobs.enabled=false` to validate the restored copy. Keep the original database until the restored copy is verified. For production, supply `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` and omit `--local`. Store periodic backups off the laptop and rehearse recovery; backup scheduling is an operator responsibility.

## Deployment settings

For the prepared **Vercel frontend + Render backend + Aiven MySQL** deployment, follow [DEPLOYMENT.md](DEPLOYMENT.md). It includes first-admin setup, verified database TLS, Resend email configuration, Razorpay test webhooks, and how GitHub pushes trigger redeployment. The configuration files do not create cloud services automatically.

Use the `prod` Spring profile with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` and `PUBLIC_APP_URL` set by the deployment environment. This profile ignores local credentials, disables API documentation, preserves an explicitly configured `MAIL_RECIPIENT_OVERRIDE` for demonstrations, requires a public URL, and validates the migrated schema. The `cloud` profile additionally configures Aiven certificate verification. Use a dedicated database account. Production adoption of an existing schema requires reviewing and explicitly baselining it; automatic baselining is disabled in this profile.

Render free blocks SMTP. Set `MAIL_PROVIDER=resend` and `RESEND_API_KEY` there; the existing notification queue sends through HTTPS. Local configuration defaults to `MAIL_PROVIDER=smtp`. Email history's `ACCEPTED` means provider acceptance, not confirmed inbox delivery. `PORT` takes precedence over `SERVER_PORT` on hosted services. Both the staff frontend and public customer payment page respect `VITE_API_BASE_URL`.

Serve the built frontend (`fswe/crmfrontend/dist`) and `/api` behind HTTPS. Set `app.cors.origins` to the exact trusted frontend origin if hosting separately; PATCH is supported. Configure client-IP handling only behind your trusted reverse proxy. The built-in limit is 10 requests per IP and authentication endpoint in 15 minutes on one application instance; use a shared gateway limiter for multiple instances. `/api/health` returns UP or DOWN without database details. Monitor this endpoint, application errors, failed mail jobs, disk usage and backup completion. Browser tokens expire after 24 hours and are stored in localStorage; a production security review should assess the session strategy and XSS protections for the hosting environment.

The implemented checks cover local functionality and migrations. They do not certify production load capacity, external email delivery, hosting/TLS configuration or compliance requirements.

Swagger: http://127.0.0.1:8085/swagger-ui/index.html
OpenAPI: http://127.0.0.1:8085/v3/api-docs
springdoc 3.1.1 supports Spring Boot 4: https://springdoc.org/
