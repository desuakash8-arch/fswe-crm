# CRM fixes and verification

See `../README.md` for current run commands, configuration, role behavior, and checks.

The previous archive verification limits have been resolved: the frontend builds on this Windows environment, lint passes, and the backend test/package build runs using the Maven wrapper. Backend integration tests run against H2; the live backend was also started successfully against the existing local MySQL database.

The startup failure was caused by the missing `app.mail.from` property. Further corrections include Vite CSS typing, Swagger compatibility, optional SMTP with bounded timeouts, correct notification JSON fields and persistence, role-aware lookup data, consistent 401/403 responses, restricted record access, timestamps, interaction edit notes/follow-up dates, and lead assignment preservation.
