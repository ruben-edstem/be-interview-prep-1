# Changelog

All notable changes to this project are recorded here, newest first.

## 0.1.3 - 2026-10-07

### Added

- Order service under `/api/v1/orders`:
  - `POST /api/v1/orders` places an order with one or more items (`productId` and `quantity`); it needs an `Idempotency-Key` header of up to 100 characters;
  - `GET /api/v1/orders/{id}` returns an order;
  - `POST /api/v1/orders/{id}/cancel` cancels an order and returns its stock; cancelling twice returns the stock only once.
- An order is all-or-nothing: if any item is short of stock, no stock is taken and no order is created. Insufficient stock returns 409 with the product, the quantity asked for and the quantity available.
- Stock is never oversold, even when many orders for the same product arrive at the same moment.
- A retried request is recognised by its `Idempotency-Key`:
  - a repeat of the same request returns the original order (200 instead of 201) and takes no more stock, including when the repeats arrive at the same moment;
  - the same key sent with different items returns 422.
- Orders take their stock from the product catalog. A cached product lookup never shows stale stock after an order is placed or cancelled.

## 0.1.2 - 2026-10-07

### Added

- Product Catalog API under `/api/v1/products`: create, list, get one, update and delete products. The catalog is seeded with 100 products on startup when it is empty.
- A product has a name, category, price (in cents), stock, rating (0 to 5) and a created date set automatically.
- The list is paged and can be sorted by any product field (`?sort=price,desc`); the response carries the total count and the number of pages. The default page size is 20 and the cap is 100, so a larger `size` returns 400.
- The list can be filtered by `category` (exact match), `minPrice` and `maxPrice` (inclusive), `inStock=true` and `name` (case-insensitive search anywhere in the name), in any combination in one request. A negative price or a `minPrice` above `maxPrice` returns 400.
- Rows with equal sort values are ordered by id as a tie-breaker, so paging never repeats or skips a product.
- Single-product lookups are cached (Caffeine, up to 10,000 products, 10 minutes). An update or delete evicts the product, so a stale product is never returned. `ProductCachingTest` shows this by counting the SQL statements Hibernate runs.
- Database indexes on category, price, stock and created date.

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
