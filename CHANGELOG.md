# Changelog

All notable changes to this project are recorded here, newest first.

## 0.1.0 - 2026-10-07

### Added

- Task Manager REST API under `/api/v1/tasks`: create, list, get one, update and delete tasks, with the list filterable by status (`?status=TO_DO|IN_PROGRESS|DONE`) and paged.
- A task has a required title (up to 100 characters), a description, a status, an optional due date that cannot be in the past, and a created date set automatically.
- Success responses are wrapped in `ApiResponse` (`success`, `data`, `message`).
- One JSON error format (`timestamp`, `status`, `code`, `message`, `fieldErrors`) for every error:
  - invalid input returns 400 with a message for each invalid field, including an unknown status, a badly formatted due date, a bad `sort` and an oversized `page`;
  - an unknown task returns 404;
  - framework errors such as an unsupported method (405), an unsupported content type (415) and an unknown path (404) keep their own status;
  - anything unexpected returns 500 with a generic message.
- Spring Boot 4.1.1 project on Java 21 with an in-memory H2 database and a Maven wrapper.
