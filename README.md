# FindMyPacks

FindMyPacks is a monolithic web application: Spring Boot serves both the REST API and the built React/Vite frontend, with PostgreSQL as its database. Docker Compose builds and runs the entire application and its tests; Java, Node.js, Maven, and npm do not need to be installed on the host.

## Requirements

- Docker Desktop with Docker Compose v2

## Start the application

From the repository root:

```sh
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080). The first startup builds the frontend and backend images, waits for PostgreSQL, runs the database migration, and creates the initial administrator.

Default local-development login:

- **User:** `findmypacksadmin`
- **Password:** `findmypacksadmin`

These are development-only credentials. Copy `.env.example` to `.env` and change `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD`, and `POSTGRES_PASSWORD` before using any shared environment. The seeded password is stored as a BCrypt hash. The administrator is created only if that username does not already exist.

To confirm the API is ready:

```sh
curl http://localhost:8080/api/web/health
```

The endpoint responds with HTTP 200 and `{"status":"UP"}`.

The login API accepts JSON at `POST /api/web/login` and starts an `HttpOnly` session:

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri http://localhost:8080/api/web/csrf -WebSession $session
Invoke-RestMethod -Uri http://localhost:8080/api/web/login -Method Post -WebSession $session `
  -Headers @{ "X-XSRF-TOKEN" = $csrf.token } -ContentType "application/json" `
  -Body (@{ username = "findmypacksadmin"; password = "findmypacksadmin" } | ConvertTo-Json)
```

## Logs and request correlation

API responses include an `X-Request-Id` header. Valid IDs supplied by callers are reused; invalid or missing IDs are replaced with a generated ID. The application request log and Hibernate SQL logs include the same `request_id`; SQL bind values are not logged.

```sh
docker compose logs -f app db
```

## Run tests

The test service runs frontend and backend tests in containers and uses the Compose PostgreSQL service:

```sh
docker compose --profile test run --build --rm tests
```

The backend produces a JaCoCo line-coverage report and enforces 100% coverage for application classes (excluding the Spring Boot entry point). The CI workflow runs this test command and builds the complete application image. To stop the application without removing database data:

```sh
docker compose down
```

To remove the local database and start fresh:

```sh
docker compose down -v
```

## Worktree collaboration

Three contributors can work asynchronously in separate worktrees and topic branches:

1. **Backend and database:** `backend/` (Spring Boot API, migrations, and API tests).
2. **Web interface:** `frontend/` (React/Vite page, login flow, and UI tests).
3. **Integration and delivery:** `compose.yaml`, `Dockerfile`, `.github/`, and documentation.

Create one worktree per contributor from the latest `main`, and keep each worktree on its own branch. For example:

```sh
git fetch origin
git worktree add ../FindMyPacks-backend -b feat/backend origin/main
```

Use corresponding distinct directory and branch names for the other contributors. Integrate changes through pull requests; never commit directly to `main`. The `main` branch is protected to require PRs and the `compose-tests` status check. The repository's pull request template is in `.github/PULL_REQUEST_TEMPLATE.md`.

## Deployment

No deployment workflow is configured yet because a deployment target has not been selected. The pull-request CI runs through Docker Compose and verifies both tests and the complete application image.
