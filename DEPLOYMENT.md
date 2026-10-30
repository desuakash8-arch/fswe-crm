# Deploy this CRM: Vercel + Render + Aiven MySQL

This guide prepares a demonstration deployment. No cloud accounts or live services have been created by these code changes. The existing laptop database and private local configuration remain separate from the cloud installation.

| Part | Hosting | Project location |
| --- | --- | --- |
| React frontend | Vercel Hobby | `fswe/crmfrontend` |
| Spring Boot API | Render free Docker web service | `fswe/crmbackend` |
| MySQL database | Aiven for MySQL free service | Managed separately from both apps |
| Notification/payment emails | Resend HTTPS API | Backend only |

Render free blocks SMTP ports, including Gmail's port 587. Local Gmail SMTP continues working; cloud mail uses `MAIL_PROVIDER=resend`. Free Render instances sleep after 15 minutes without inbound traffic. Opening the app can take around a minute after sleep, and scheduled reminders/email jobs do not run while it sleeps. Open the app before your presentation. See [Render free limits](https://render.com/docs/free).

Vercel Hobby is for personal, non-commercial projects. Aiven's free MySQL currently provides 1 GB storage; unused services can be stopped. These plans suit a college demonstration. See [Vercel Hobby](https://vercel.com/docs/plans/hobby) and [Aiven free MySQL](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier).

## 1. Put the prepared source on GitHub

In Windows PowerShell, use `npm.cmd` for local npm commands if `npm.ps1` is blocked by the execution policy. Git for Windows must also be installed and available in `git --version`; `npm install git` installs an unrelated npm package. After installing Git, reopen PowerShell (or restart VS Code if using its terminal) so the updated PATH is loaded.

Create an empty **private** GitHub repository named, for example, `fswe-crm`. Do not initialize it with a README because this project already has one. From PowerShell:

```powershell
Set-Location C:\Users\AKASH\fswe-company-role-updated
git init -b main
git add .
git diff --cached --name-only
```

Review the staged filenames before committing. The ignore rules exclude local credentials, `.env` files, SQL backups, logs, build output, dependencies and certificates. Never add `application-local.properties`, database exports or API secrets with `git add -f`. Secrets belong in the hosting dashboard, not the repository. GitHub may request sign-in; complete it in your browser.

```powershell
git commit -m "Prepare CRM for cloud deployment"
git remote add origin https://github.com/YOUR-USERNAME/fswe-crm.git
git push -u origin main
```

Replace `YOUR-USERNAME` with your account. These initialization commands are needed only once. On an existing Git repository, inspect its branch/remotes rather than reinitializing it.

## 2. Create Aiven MySQL

1. Create an Aiven account and select a **free MySQL** service, rather than a paid trial plan. Aiven currently assigns the cloud provider and region on the Free tier; use that assigned region. See [Aiven service setup](https://aiven.io/docs/products/mysql/get-started).
2. Wait until the service is running. Save the hostname, port, database name, username and password privately.
3. Download the project's CA certificate from the connection information. It will be uploaded to Render as `aiven-ca.pem`.
4. Choose how to start: a fresh database uses the first-admin setup below; an existing-data deployment must import the laptop database before starting the backend.

The Render database URL should contain no credentials or SSL overrides:

```text
jdbc:mysql://YOUR-AIVEN-HOST:YOUR-AIVEN-PORT/defaultdb
```

Replace `defaultdb` if using another database. Username/password are separate environment variables. The `cloud` profile verifies both the CA and database hostname using `VERIFY_IDENTITY`; do not turn off SSL to solve a connection error. The Docker entry point imports the certificate into a MySQL-only trust store. Flyway and the application's connection pool both use it. See [Aiven certificates](https://aiven.io/docs/platform/concepts/tls-ssl-certificates).

To retain existing laptop data, run `npm.cmd run backup:db` in PowerShell, then import the private SQL file into a new empty Aiven database using MySQL Workbench's Data Import. Configure Workbench with the Aiven host/port, CA certificate and verified TLS. Include `flyway_schema_history` so the backend recognizes already-applied migrations. Point `DB_URL` at that database and leave bootstrap disabled. Do not import into a database already populated by the deployed application. This preparation does not copy or upload any records.

An imported `email_jobs` table can contain pending messages with old localhost links. Keep `MAIL_ENABLED=false` and `BACKGROUND_JOBS_ENABLED=false` while reviewing those jobs; do not replay old payment links. Generate new links after setting the final public URL. For a clean presentation, a fresh cloud database avoids this import step.

## 3. Create the Vercel project

Import the GitHub repository into Vercel and choose:

| Setting | Value |
| --- | --- |
| Root Directory | `fswe/crmfrontend` |
| Framework | Vite |
| Install Command | `npm ci` |
| Build Command | `npm run build` |
| Output Directory | `dist` |
| Node.js | 24.x |
| Production Branch | `main` |

Set `VITE_API_BASE_URL` to `https://YOUR-BACKEND.onrender.com/api`. If the Render service does not exist yet, temporarily enter `https://replace-after-render-setup.onrender.com/api` to obtain the frontend URL. This first frontend build cannot log in until the actual backend address is supplied in step 5.

Save Vercel's stable production URL, for example `https://YOUR-FRONTEND.vercel.app`, rather than a commit-specific preview URL. The included `vercel.json` handles refreshing routes such as `/customers`, `/deals` and `/pay`. Both staff APIs and customer checkout use the configured backend URL.

Only the public API address belongs in a `VITE_` variable: these values are included in downloaded JavaScript. Never place the Razorpay secret, JWT secret, database password or Resend key in Vercel frontend variables. See [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite).

## 4. Create the Render backend

Use **New → Blueprint**, connect the same repository, and select the root `render.yaml`. It selects the free Docker plan and backend directory. Before the first successful start, open the service settings and add a **Secret File** named `aiven-ca.pem` containing the downloaded certificate. Render mounts it at `/etc/secrets/aiven-ca.pem`. If the initial deployment starts before the file is added, add it and redeploy.

Supply these values when prompted:

| Variable | Value |
| --- | --- |
| `DB_URL` | `jdbc:mysql://HOST:PORT/DATABASE` from Aiven, without credentials or query parameters |
| `DB_USERNAME` | Aiven database username |
| `DB_PASSWORD` | Aiven database password |
| `PUBLIC_APP_URL` | Exact stable Vercel origin, e.g. `https://YOUR-FRONTEND.vercel.app`, without a trailing slash |

The Blueprint sets `SPRING_PROFILES_ACTIVE=prod,cloud`, creates a random `JWT_SECRET`, selects the Asia/Kolkata timezone and uses `/api/health` for health checks. Render supplies `PORT`; the application now reads it. Do not put MySQL on Render's temporary filesystem. Database records live in Aiven.

For a **fresh empty database**, temporarily add these Render environment values:

```text
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_NAME=Akash
BOOTSTRAP_ADMIN_EMAIL=desuakash8@gmail.com
BOOTSTRAP_ADMIN_PASSWORD=<choose a private password of 10–72 characters>
```

Save and deploy. Flyway creates the schema and the bootstrap creates one Admin with a hashed password only when no users exist. After successful login, set `BOOTSTRAP_ADMIN_ENABLED=false` and delete `BOOTSTRAP_ADMIN_PASSWORD`. Existing accounts are never reset by this setup. Create companies, Managers and Sales users from the CRM's existing screens.

The Blueprint starts with mail disabled. This lets you verify database/login setup before configuring the email provider. Keep the generated `JWT_SECRET` stable across deployments; rotating it signs everyone out.

## 5. Connect the two public URLs

1. Copy Render's actual HTTPS service URL and set Vercel `VITE_API_BASE_URL` to that URL followed by `/api`.
2. **Redeploy Vercel.** Vite reads this variable at build time; changing the dashboard value alone does not update an existing build.
3. Confirm Render `PUBLIC_APP_URL` exactly matches the stable Vercel origin, then redeploy Render if changed. This controls CORS, password-reset URLs and emailed payment links.
4. Open `https://YOUR-BACKEND.onrender.com/api/health`; expect `{"status":"UP"}`. Open the Vercel URL and sign in with your cloud Admin.

Preview branches receive separate Vercel URLs. They are not automatically authorized by backend CORS. Use a separately configured test backend for active preview testing; do not allow every `*.vercel.app` domain against your main CRM database.

## 6. Enable emails and Razorpay test payments

Create a Resend account using **desuakash8@gmail.com**, then create an API key. The default `onboarding@resend.dev` sender is limited to the account owner's email, which matches your demo routing. A verified domain is needed later to email real customers. See [Resend's test-sender restriction](https://resend.com/docs/knowledge-base/403-error-resend-dev-domain).

Set privately in Render:

```text
RESEND_API_KEY=<your private Resend key>
MAIL_PROVIDER=resend
MAIL_FROM=onboarding@resend.dev
MAIL_RECIPIENT_OVERRIDE=desuakash8@gmail.com
RAZORPAY_KEY_ID=<your rzp_test_... key ID>
RAZORPAY_KEY_SECRET=<your private test key secret>
RAZORPAY_WEBHOOK_SECRET=<a separate private webhook signing secret>
```

After adding the key, change the `MAIL_ENABLED` value in `render.yaml` from `"false"` to `"true"`, commit and push. This keeps the Blueprint consistent with the service instead of a later Blueprint sync switching mail off. Keep the recipient override because customer addresses in this project are examples. Mail still says `[LOCAL TEST]` to identify redirected demonstration messages, even when hosted online. The local SMTP configuration remains available when running on the laptop.

In Razorpay **Test mode**, enable automatic capture and register this webhook:

```text
https://YOUR-BACKEND.onrender.com/api/public/payments/webhook
```

Use the same webhook secret and subscribe to `payment.captured`, `payment.failed`, `payment.authorized` and `order.paid`. Create a won deal/invoice and use **Email payment link**. Verify that the message arrives in your inbox, the link opens the public `/pay` page without CRM login, and a Razorpay test payment becomes Paid. The backend verifies payment with Razorpay; no real money is charged. Free-instance cold starts can delay webhook processing, so **Check payment status** remains available.

## 7. How future GitHub updates redeploy

The normal update path is:

```text
Edit on laptop → test → commit → push to main
                              ├─ Vercel builds the frontend → same production URL
                              └─ GitHub checks pass → Render builds the API → same API URL
                                                                 │
                                                       existing Aiven database
```

Saving files locally does not change the deployed website. A commit records the change locally; `git push` sends it to GitHub, where the connected hosting services detect it. Render is configured with `autoDeployTrigger: checksPass`, so it waits for the repository's GitHub Actions checks. Vercel normally starts its own build on the push; it does not inherit Render's wait-for-CI setting. The two deployments are independent, not an atomic release. Render's backend root directory means frontend-only changes do not require a backend rebuild. See [Render deployment triggers](https://render.com/docs/blueprint-spec) and [Vercel GitHub deployments](https://vercel.com/docs/git/vercel-for-github).

For ordinary changes, from the project root:

```powershell
git pull --ff-only
# Make and save your changes.
npm.cmd run verify
git status
git add .
git diff --cached --stat
git commit -m "Describe the CRM change"
git push origin main
```

Wait for GitHub Actions, Vercel and Render to report success, then refresh the stable Vercel URL. If a build fails, inspect that provider's deployment log, fix the error and push again. Successful deployments replace the running application; they do not replace the Aiven database.

For larger work, use `git switch -c feature/my-change`, commit and push that branch, then open a pull request. Verify checks before merging into `main`. This keeps unfinished work off your production branch. Keep API changes compatible while the frontend and backend deploy at different times.

Important differences:

- **CSS or React changes:** rebuild the frontend. The existing theme is unchanged by this hosting preparation.
- **Java/backend changes:** Render builds a new Docker image and restarts the service.
- **Database structure changes:** add the next Flyway migration, such as `V5__New_feature.java`, alongside the existing migrations. Never edit an applied migration or switch Hibernate to `create`/`create-drop`. Startup applies only new migrations; customer/deal records stay in place unless the migration explicitly alters/deletes them. Back up before structural changes.
- **Data entered in the CRM:** saved directly to Aiven. No Git push is needed. Data entered in the laptop's local MySQL database does not automatically sync to Aiven.
- **Passwords/API keys:** update Render environment secrets, then redeploy. Do not commit them. Changing a frontend environment setting requires a new Vercel build.
- **Rollback:** reverting a Git commit and pushing can restore application code. It does not undo a database migration, so database changes need their own recovery plan.

## Deployment checks

Before sharing the demo, verify Admin/Manager/Sales login, page refresh on a nested route, customer/lead/deal saves, deal-health explanations, notification email delivery, and one complete Razorpay test checkout from an emailed link. Confirm records still exist after a Render redeploy. Cloud connectivity, actual provider delivery and resource limits must be checked on the deployed services; local automated tests use isolated data and mocked payment/email providers.

Preparation checks on 30 October 2026: 73 backend tests passed, one optional live SMTP test skipped, and all five browser scenarios passed with the frontend calling a separate backend origin. Frontend build/lint, backend JAR packaging, production-profile first-admin startup, hosting configuration schema checks and the VS Code Java compiler warning check passed. The build also correctly rejects a missing Vercel API URL. Docker is not installed on this laptop, so the Docker image itself and connections to actual cloud services remain to be verified during deployment.
