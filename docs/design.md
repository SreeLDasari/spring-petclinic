\# Design: Appointment Reminder \& Notification System (EPIC MLG-1)



\## User Stories

\- MLG-2: Owner receives email reminder 24h before pet's visit

\- MLG-3: Admin/Vet configures reminder lead-time (24h/48h)

\- MLG-4: Owner sees "Reminder Sent" status on visit in UI

\- MLG-5: System runs scheduled job to trigger reminders automatically



\## Database Change

Add to `visits` table (and corresponding Visit entity):

\- `reminder\_sent` BOOLEAN DEFAULT FALSE

\- `reminder\_sent\_at` TIMESTAMP NULL



\## Backend Components

\- `ReminderService`

&#x20; - `findPendingReminders()` — queries visits within configured lead-time window, reminder\_sent = false

&#x20; - `sendReminder(Visit visit)` — sends email via `JavaMailSender`, then sets reminder\_sent=true, reminder\_sent\_at=now()

\- `ReminderSchedulerConfig`

&#x20; - `@Scheduled(cron = "...")` method calling `ReminderService.findPendingReminders()` then `sendReminder()` for each

\- `NotificationController` (optional)

&#x20; - `POST /api/reminders/trigger` — manually triggers the reminder scan (for testing)



\## Frontend

\- Update visit list/detail Thymeleaf template to show a badge:

&#x20; - "Reminder Sent ✅" if reminder\_sent = true

&#x20; - "Pending ⏳" if false and visit is upcoming



\## Config

\- `reminder.lead-time-hours` property (default 24), overridable per environment

\- SMTP settings via environment variables: `MAIL\_HOST`, `MAIL\_PORT`, `MAIL\_USERNAME`, `MAIL\_PASSWORD`



\## Tests

\- Unit: ReminderService (mock mail sender, mock repository)

\- Integration: scheduler fires, visit updated

\- Gherkin/Playwright: covered in later QA step — not part of this story set

