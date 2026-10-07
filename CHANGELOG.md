# Changelog

All notable changes to this project are recorded here, newest first.

## 0.1.1 - 2026-10-07

### Added

- URL shortener under `/api/v1/urls` and `/{code}`:
  - `POST /api/v1/urls` takes an `http` or `https` URL (up to 2048 characters) and an optional future `expiresAt`, and returns a short code of 8 letters and digits with the short URL;
  - `GET /{code}` redirects (302) to the original URL and counts the visit; an unknown code returns 404 and an expired one returns 410;
  - `GET /api/v1/urls/{code}/stats` returns the original URL, the visit count and the created date.
- Shortening the same URL twice returns two different codes, each with its own expiry and visit count.
- Visit counts stay exact when many people open the same link at once.
- `shortener.base-url` property (environment variable `SHORTENER_BASE_URL`, default `http://localhost:8080`) sets the host used in short URLs.

## 0.1.0 - 2026-10-07

### Added

- Task Manager REST API under `/api/v1/tasks`: create, list, get one, update and delete tasks, with the list filterable by status (`?status=TO_DO|IN_PROGRESS|DONE`) and paged.
- A task has a required title (up to 100 characters), a description (up to 2000 characters), a status that defaults to `TO_DO` when omitted on create, an optional due date that cannot be in the past, and a created date set automatically.
- Success responses are wrapped in `ApiResponse` (`success`, `data`, `message`).
- One JSON error format (`timestamp`, `status`, `code`, `message`, `fieldErrors`) for every error:
  - invalid input returns 400 with a message for each invalid field, including an unknown status, a badly formatted due date, a bad `sort` and an oversized `page`;
  - an unknown task returns 404;
  - framework errors such as an unsupported method (405), an unsupported content type (415) and an unknown path (404) keep their own status;
  - anything unexpected returns 500 with a generic message.
- `app.version` property, filled in from the project version at build time.
- Spring Boot 4.1.1 project on Java 21 with an in-memory H2 database and a Maven wrapper, using the `com.mock` group id and `com.mock.taskmanager` package.
