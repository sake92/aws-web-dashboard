# AGENTS.md — AWS Web Dashboard

A web-based AWS management dashboard for local development, built with Scala 3, HTMX, and Bulma CSS. Manages S3 buckets/objects and SQS queues against a local AWS emulator (Floci/LocalStack) running on port 4566.

---

## Build & Run

This project uses **Deder** (not sbt, Maven, or Gradle).

```bash
# Start local AWS emulator (Floci) on port 4566
docker compose up -d

# Run the web app on port 8181
deder exec -t run -m app

# Install BSP for IDE support
deder bsp install
```

The app is available at `http://localhost:8181` once running.

---

## Tech Stack

| Layer       | Technology                             |
|-------------|----------------------------------------|
| Language    | Scala 3 (3.7.3)                        |
| HTTP server | Sharaf + Undertow                      |
| Frontend    | HTMX + Bulma CSS                       |
| AWS SDK     | software.amazon.awssdk (S3, SQS 2.x)  |
| Testing     | MUnit                                  |
| Build       | Deder                                  |
| Local AWS   | Floci via Docker Compose               |

---

## Project Structure

```
app/src/
  Main.scala       # AWS client setup, route wiring, server start (port 8181)
  Views.scala      # Base HTML template, nav, error components
  S3Routes.scala   # Bucket/object CRUD — list, create, upload, download, delete
  SqsRoutes.scala  # Queue/message management — list, send, receive (SSE), purge
app/resources/public/
  htmx.js                # HTMX library
  htmx-ext-sse.min.js    # SSE extension for real-time message streaming
  bulma.min.css          # Bulma CSS framework
  custom.css             # Project-specific overrides
deder.pkl                # Build config (deps, Scala version, main class: app.Main)
docker-compose.yml       # Floci service definition
.scalafmt.conf           # Formatter config (Scala 3, 120-char line limit)
```

---

## Code Conventions

- **Formatting**: Enforced by Scalafmt. Run `scalafmt` before committing. Max line length: 120 chars. Dialect: Scala 3.
- **HTML templating**: Done via Scala string interpolation inside route handlers and `Views.scala`. No separate template engine.
- **Route files**: One file per AWS service (`S3Routes.scala`, `SqsRoutes.scala`). Add new services in a new `<Service>Routes.scala` and wire it in `Main.scala`.
- **HTMX patterns**: Forms POST to the same or related path; responses return HTML fragments. Use `hx-target` and `hx-swap` on the form side.
- **Server-Sent Events**: SQS message receive uses SSE (`htmx-ext-sse`). The server streams events over a long-lived connection.

---

## AWS Configuration

Credentials and endpoint are hardcoded for local dev — do not change them for production use:

```
Endpoint:    http://localhost:4566
Region:      US_EAST_1
Access key:  test
Secret key:  test
```

Both S3 and SQS clients are initialized in `Main.scala` and passed to their respective route handlers.

---

## Testing

Uses **MUnit**. Run tests with:

```bash
deder exec -t test -m app-test
```

Tests are in `app/test/`. The project is small; prefer testing route logic and AWS interactions with a live Floci instance rather than mocking the SDK.

---

## Common Tasks

**Add a new S3 operation:**
1. Add the route in `S3Routes.scala`.
2. Add any new HTML form/fragment in the same file or in `Views.scala` if reusable.
3. Wire the route in `Main.scala` if it needs a new path prefix.

**Add a new SQS operation:**
Same pattern as S3 — `SqsRoutes.scala` → `Main.scala`.

**Add support for a new AWS service:**
1. Add the SDK dependency in `deder.pkl`.
2. Create `<Service>Routes.scala` following the existing pattern.
3. Initialize the client in `Main.scala` and pass it to the new routes.
4. Add a nav link in `Views.scala`.

**Change the server port:**
Edit `Main.scala` — the port is set when starting the Undertow server.

---

## What to Avoid

- Do not add a JavaScript build pipeline (webpack, vite, etc.) — the project intentionally uses HTMX to avoid JS complexity.
- Do not introduce sbt or other build tools alongside Deder.
- Do not commit real AWS credentials. The test credentials (`test`/`test`) are intentional for local dev only.
- Do not add abstractions for one-off operations — route handlers are intentionally direct and imperative.
