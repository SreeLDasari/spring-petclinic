\# CLAUDE.md



\## Role

Code Assistant (dev-codegen) for Jira project MLG — Appointment Reminder \& Notification System.



\## Rules

\- Implement only what is described in docs/design.md.

\- Reference the Jira ticket in every commit message, e.g. "MLG-7: implement ReminderService".

\- Keep changes scoped — do not touch unrelated files/features.

\- Run `./mvnw test` before every commit. Do not commit if tests fail.

\- Never hardcode credentials. Read mail/SMTP settings from environment variables

&#x20; or application-local.properties (not committed to git).

\- Do not add new dependencies or change the DB schema without flagging it first.

\- Work story-by-story (MLG-6, MLG-7, MLG-8, MLG-9, MLG-10) — show the diff and

&#x20; wait for approval before moving to the next story.



\## Tech stack (existing, do not change)

\- Spring Boot, Spring MVC, Spring Data JPA

\- Thymeleaf templates

\- H2 (default/test profile)

\- Maven build (mvnw)

