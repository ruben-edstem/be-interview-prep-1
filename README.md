# be-interview-prep-1

Backend interview prep: Java 21, Spring Boot, Maven.

```bash
./mvnw test
./mvnw spring-boot:run
```

## Q2 — URL Shortener

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/v1/urls` | `201` with `code` and `shortUrl`. Body: `{"url": "https://...", "expiresAt": "2030-01-01T00:00:00Z"}`; `expiresAt` is optional and must be in the future. |
| `GET` | `/{code}` | `302` to the original URL and counts the visit. `404` for an unknown code, `410` for an expired one. |
| `GET` | `/api/v1/urls/{code}/stats` | Original URL, visit count and created date. `404` for an unknown code. |

Invalid URLs (anything that is not `http` or `https`, blank, or over 2048 characters) get `400`.

Configuration comes from the environment: `SHORTENER_BASE_URL` (default `http://localhost:8080`),
`DATASOURCE_URL`, `DATASOURCE_USERNAME`, `DATASOURCE_PASSWORD`, `JPA_DDL_AUTO`. The default datasource
is in-memory H2, so data does not survive a restart.

### Decisions

- **Shortening the same URL twice returns two different codes.** Each link carries its own optional
  expiry and its own visit count, so two callers who shorten the same page must not share or overwrite
  each other's settings and statistics. Reusing one code would also make an expiry set by one caller
  silently cut off the other. The cost is more rows for repeated URLs, which is cheap.
- **Codes are 8 random base62 characters** from `SecureRandom` (62^8, about 2 x 10^14 values), so they
  are URL-safe and not guessable in sequence. Uniqueness is enforced by a database unique constraint;
  a collision is retried a few times before the request fails with `503`.
- **Redirects are `302`, not `301`.** A cached permanent redirect would let browsers skip the server,
  so visits would go uncounted. The response also carries `Cache-Control: no-store`.
- **Visit counts are exact under concurrency** because the increment is a single
  `UPDATE ... SET visit_count = visit_count + 1` executed by the database, not a read-modify-write in
  Java. `UrlShortenerIntegrationTest` fires 200 visits from 16 threads and asserts the exact total.
- **Expired links return `410 Gone`** and are not counted as visits; a link is expired from the instant
  of `expiresAt`.
