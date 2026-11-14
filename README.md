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

## Command-line interface (Issue #7)

The CLI uses the REST API and never connects directly to PostgreSQL. Keep the application running and execute every CLI command through Docker Compose:

```sh
docker compose up --build -d --wait
docker compose --profile cli run --rm cli help
docker compose --profile cli run --rm cli health
docker compose --profile cli run --rm cli token findmypacksadmin findmypacksadmin
```

The `token` command prints a Bearer token. Put it in `FINDMYPACKS_TOKEN` before using the protected status command:

```sh
export FINDMYPACKS_TOKEN="token-returned-by-the-command"
docker compose --profile cli run --rm cli status
```

PowerShell:

```powershell
$env:FINDMYPACKS_TOKEN = "token-returned-by-the-command"
docker compose --profile cli run --rm cli status
```

`GET /api/cli/health` is public. `POST /api/cli/token` validates the initial username and password. Protected CLI endpoints require `Authorization: Bearer <token>` and return HTTP 401 for a missing or invalid token. Change the development credentials through `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`; do not commit real credentials or tokens.

## Package registration and editing through the CLI (Issue #9)

The CLI uses the same six fields and validation rules documented in the Web package form. Set `FINDMYPACKS_TOKEN`, then run:

```sh
docker compose --profile cli run --rm cli create BR12345678 "Maria Silva" Fortaleza CORREIOS POSTED 2026-11-12
docker compose --profile cli run --rm cli update 1 BR12345678 "Maria Souza" Recife JADLOG IN_TRANSIT 2026-11-12
```

The arguments are, in order: tracking code, customer, destination, carrier, status, and posting date. The `update` command also receives the package ID before those fields. Quote customer or destination values that contain spaces.

The commands call the protected endpoints `POST /api/cli/records` and `PUT /api/cli/records/{id}`. They return HTTP 401 without a valid Bearer token, HTTP 400 for invalid fields, HTTP 404 when an update ID does not exist, and HTTP 409 for a duplicated tracking code. Saved records use the same PostgreSQL table as the Web application, so they are visible in the Web list immediately.

Create and update operations write correlated JSON events for the `api` and `db` layers with `client` set to `cli`. Inspect them with `docker compose logs -f app`; passwords, Bearer tokens, and request bodies are not logged.

## Package listing and deletion through the CLI (Issue #11)

With `FINDMYPACKS_TOKEN` set, list packages five at a time. The page is zero-based; the optional sort field and direction default to `postedAt` and `desc`:

```sh
docker compose --profile cli run --rm cli list
docker compose --profile cli run --rm cli list 0 trackingCode asc
```

Delete a package by its identifier:

```sh
docker compose --profile cli run --rm cli delete 1
```

The commands call `GET /api/cli/records?page=0&size=5&sort=trackingCode,asc` and `DELETE /api/cli/records/{id}`. Both require a valid Bearer token and return HTTP 401 without it. Deleting an unknown identifier returns HTTP 404. Deletions use the same PostgreSQL records as the Web application and therefore disappear from the Web list.

List and delete operations produce correlated JSON events in the API and database layers with `client` set to `cli`. View them with `docker compose logs -f app`.

## Advanced package search through the CLI (Issue #10)

The CLI can combine the same filters as the Web search: tracking code, customer, destination, carrier, and status. Text fields match partial values without case sensitivity; carrier and status match their allowed codes without case sensitivity. Blank filters are ignored. Set `FINDMYPACKS_TOKEN` and pass any combination of the filters:

```sh
docker compose --profile cli run --rm cli search --customer "Maria Silva" --destination Fortaleza --status IN_TRANSIT
docker compose --profile cli run --rm cli search --tracking-code BR123 --carrier CORREIOS --page 0 --size 10 --sort postedAt --direction desc
```

The search command calls the protected `GET /api/cli/records/search` endpoint; it never accesses PostgreSQL directly. The endpoint accepts `trackingCode`, `customer`, `destination`, `carrier`, `status`, `page`, `size`, and `sort`, with the same matching, validation, paging, and sorting behavior as `GET /api/web/records/search`. Sort fields are `trackingCode`, `customer`, `destination`, and `postedAt`; page numbering starts at zero and page size must be from 1 to 100. Invalid filters or paging/sort values return HTTP 400, and a missing or invalid Bearer token returns HTTP 401.

Each CLI search records correlated structured `api` and `db` events with operation `search_packages` and `client` set to `cli`. Inspect them using `docker compose logs -f app`.

## Dashboard indicators through the CLI (Issue #12)

With `FINDMYPACKS_TOKEN` set, display the three dashboard indicators as text:

```sh
docker compose --profile cli run --rm cli indicators
```

The command calls the protected `GET /api/cli/dashboard` endpoint and displays package counts by status, package counts by carrier, and the number of packages posted on each of the last seven days. It uses the same summary and date window as `GET /api/web/dashboard`, including zero-count categories and dates, so the values match the Web dashboard. The CLI calls the API and does not connect to PostgreSQL directly.

Each request writes correlated structured `api` and `db` events using operation `dashboard` and `dashboard_summary`, respectively, with `client` set to `cli`. Inspect them using `docker compose logs -f app`. A missing or invalid Bearer token returns HTTP 401.

## Logs and request correlation

Each Web and CLI operation writes a single-line JSON event to stdout with an ISO 8601 UTC timestamp, level, layer, client, operation, status, request ID, and a short message. Database reads made by an operation produce a separate `db` event with the same request ID and client. Successful and failed login attempts are logged without passwords or request payloads. SQL statements and bind values are not logged.

```sh
docker compose logs -f app
```

API responses include an `X-Request-Id` header. Valid IDs supplied by callers are reused; invalid or missing IDs are replaced with a generated ID. To inspect the CLI API and database logs, run:

```sh
docker compose logs -f app
```

The protected `status` command produces one `api` event and one `db` event with `client` set to `cli` and the same `request_id`. Credentials and tokens are never included in these logs.

## Package registration and editing (issue #2)

After signing in, the home page shows a form to register and edit packages. Every field is required.

| Field | Form label | Rule |
|---|---|---|
| `trackingCode` | Código de rastreio | 8 to 20 letters or digits; must be unique |
| `customer` | Cliente | 2 to 100 characters |
| `destination` | Destino | 2 to 100 characters |
| `carrier` | Transportadora | One of `CORREIOS`, `JADLOG`, `LOGGI`, `OTHER` |
| `status` | Status | One of `POSTED`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED` |
| `postedAt` | Data de postagem | ISO date (`yyyy-MM-dd`); cannot be in the future |

The form shows the Portuguese labels (for example "Em trânsito"), but sends the codes above to the API. Text fields are trimmed before validation. The same rules are enforced in the form, in the API (Bean Validation), and by constraints in the database.

**How to use it:**

1. Sign in and fill in the form.
2. Click **Cadastrar pacote**. On success the page shows "Pacote cadastrado." and the form switches to editing the saved record ("Editar pacote #N").
3. Change any field and click **Salvar alterações** to update it ("Pacote atualizado.").
4. Click **Novo cadastro** to clear the form and register another package.

Validation errors appear under each field. A tracking code that is already registered is rejected with a message on that field.

### Data model

The migration `backend/src/main/resources/db/migration/V2__create_packages.sql` creates the `packages` table:

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL` | Primary key |
| `tracking_code` | `VARCHAR(20)` | Not null, unique |
| `customer` | `VARCHAR(100)` | Not null |
| `destination` | `VARCHAR(100)` | Not null |
| `carrier` | `VARCHAR(20)` | Not null; check constraint on the allowed values |
| `status` | `VARCHAR(20)` | Not null; check constraint on the allowed values |
| `posted_at` | `DATE` | Not null |

To inspect the stored data:

```sh
docker compose exec db psql -U findmypacks -d findmypacks -c "SELECT * FROM packages;"
```

## Package listing and deletion (issue #3)

After signing in, the home page loads packages from the database and displays them in a table. The table shows the tracking code, customer, destination, and posting date. It displays five packages per page and includes previous/next controls and the total number of records. Use the column headings for tracking code, customer, destination, or posting date to sort the results; clicking the active heading again reverses the sort direction.

Select individual rows with their checkboxes, or select all rows on the current page with the checkbox in the table heading. Click **Excluir selecionados** and confirm to delete the selected packages. The list reloads after deletion. Click **Editar** on a row to load that package in the registration form above.

The list endpoint supports Spring pagination and sorting parameters. The Web page requests five rows at a time:

| Operation | Method and path | Success |
|---|---|---|
| List packages | `GET /api/web/records?page=0&size=5&sort=postedAt,desc` | `200` with a paginated JSON response |
| Delete a package | `DELETE /api/web/records/{id}` | `204` with no response body |

Both endpoints require an authenticated session. The delete request also sends the CSRF token in the `X-XSRF-TOKEN` header. A list request returns HTTP 401 without a valid session; deleting an unknown ID returns HTTP 404. List and delete requests are recorded in the API logs, and their database reads/writes are logged with the same request ID.

## Advanced package search (issue #4)

The authenticated Web search endpoint is `GET /api/web/records/search`. It accepts any combination of `trackingCode`, `customer`, `destination`, `carrier`, and `status`; tracking code, customer, and destination match partial text without case sensitivity, while carrier and status match their allowed values without case sensitivity. Blank filters are ignored. The response uses the same page structure as the list endpoint, so `page`, `size`, and `sort` work together with the filters. Invalid carrier/status values or malformed paging parameters return HTTP 400; a request without a session returns HTTP 401.

Example: `GET /api/web/records/search?customer=maria&status=IN_TRANSIT&page=0&size=5&sort=postedAt,desc`. Each search emits correlated `api` and `db` JSON logs using operation `search_packages`.

The frontend behavior is covered by `PackageList.test.tsx`. Backend coverage is in `PackageListingApiIntegrationTest` and `PackageListingDataAccessTest`.

## CSV export (issue #5)

The authenticated Web endpoint `GET /api/web/records/export` downloads every package matching the supplied filters, regardless of the current list page. It accepts the same filters as advanced search—`trackingCode`, `customer`, `destination`, `carrier`, and `status`—plus `sort`. It does not accept `page` or `size`. Text filters use case-insensitive partial matching; `carrier` and `status` must be valid values. Sorting uses the same allowed fields and directions as search (`trackingCode`, `customer`, `destination`, or `postedAt`; `asc` or `desc`) and defaults to `postedAt,desc`. Blank filters are ignored. With no filters, the request is `/api/web/records/export?sort=postedAt,desc`.

The response is `200` with `Content-Type: text/csv; charset=UTF-8` and `Content-Disposition: attachment; filename="pacotes.csv"`. Columns are emitted in this order: `trackingCode`, `customer`, `destination`, `carrier`, `status`, `postedAt`. Rows are separated by CRLF. A field containing a comma, quote, or line break is enclosed in double quotes, and quotes inside it are doubled. Text cells beginning with `=`, `+`, `-`, or `@` are prefixed with an apostrophe to prevent spreadsheet formula execution. If no packages match, the CSV contains only the header row.

Example using the authenticated `$session` created in the login example above; this downloads all matching records to `pacotes.csv`:

```powershell
$url = "http://localhost:8080/api/web/records/export?customer=maria&status=IN_TRANSIT&sort=postedAt,desc"
Invoke-WebRequest -Uri $url -WebSession $session -OutFile pacotes.csv
```

The frontend **Exportar CSV** button uses the filters currently applied by **Buscar** and the table's active sort. The export request is logged as `export_packages` at both the `api` and `db` layers, with the same `request_id`; follow the request with `docker compose logs app` or a supplied `X-Request-Id`. Tests are `PackageExportApiIntegrationTest`, the export success/failure cases in `PackageListingDataAccessTest`, and the export cases in `PackageList.test.tsx`. Run all tests with `docker compose --profile test run --build --rm tests`.

### Endpoints

Both endpoints require an authenticated session and a CSRF token (`GET /api/web/csrf`, sent in the `X-XSRF-TOKEN` header). Requests without a valid session receive HTTP 401. The JSON body uses the camelCase field names from the table above.

| Operation | Method and path | Success |
|---|---|---|
| Register a package | `POST /api/web/records` | `201` with the saved record |
| Edit a package | `PUT /api/web/records/{id}` | `200` with the updated record |

Error responses use the body `{"message": "...", "fieldErrors": {"field": "message"}}`:

| Status | When |
|---|---|
| `400` | A field is invalid (`fieldErrors` lists each one) or the body is malformed |
| `401` | No valid session |
| `404` | `PUT` with an `id` that does not exist |
| `409` | The tracking code is already registered by another package |

Example with PowerShell, reusing the `$session` from the login example above:

```powershell
$csrf = Invoke-RestMethod -Uri http://localhost:8080/api/web/csrf -WebSession $session
$body = @{
  trackingCode = "BR12345678"; customer = "Maria Silva"; destination = "Fortaleza"
  carrier = "CORREIOS"; status = "POSTED"; postedAt = "2026-11-10"
} | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8080/api/web/records -Method Post -WebSession $session `
  -Headers @{ "X-XSRF-TOKEN" = $csrf.token; "X-Request-Id" = "manual-test-1" } `
  -ContentType "application/json" -Body $body
```

A successful response looks like this:

```json
{
  "id": 1,
  "trackingCode": "BR12345678",
  "customer": "Maria Silva",
  "destination": "Fortaleza",
  "carrier": "CORREIOS",
  "status": "POSTED",
  "postedAt": "2026-11-10"
}
```

To edit, send the same body with `-Method Put` to `http://localhost:8080/api/web/records/1`.

## Dashboard (issue #6)

After signing in, the dashboard requests `GET /api/web/dashboard` and displays three charts based on persisted `packages` records:

- **Packages by status:** count of every package in the database, grouped by its current status.
- **Packages by carrier:** count of every package in the database, grouped by carrier.
- **Posts in the last 7 days:** number of packages by `postedAt` date, from today minus six days through today. Days with no packages are shown as zero.

The API computes these aggregates directly from PostgreSQL; the browser fetches them on sign-in/page load, and refreshes them after a package is created or edited. Use **Atualizar** to fetch the latest values without reloading the page. The endpoint requires an authenticated session and returns `byStatus`, `byCarrier`, and `postedPerDay` arrays.

### Logs for these operations

Registering a package logs an `api` event with the operation `create`, and editing logs `update`. The same request also produces `db` events with the same `request_id`: `check_tracking_code`, `find_package_by_id` (edit only), and `insert` or `update`. To follow one request, reuse the `X-Request-Id` header and filter the logs:

```sh
docker compose logs app | grep manual-test-1
```

On PowerShell, use `docker compose logs app | Select-String manual-test-1`. Passwords, tokens, and request payloads are not logged.

### Tests

The backend tests for this feature are `PackageDataAccessTest` (data access and `db` logs), `PackageApiIntegrationTest` (endpoints, validation, authentication, and correlated logs), and `AdminUserSeederTest` (keeps the 100% coverage independent of the database state). The form is covered by `PackageForm.test.tsx`. Run them with the command in the next section.

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

1. **Backend and database:** `backend/` (Spring Boot API, migrations, CLI, and API tests).
2. **Web interface:** `frontend/` (React/Vite page, login flow, and UI tests).
3. **Integration and delivery:** `compose.yaml`, `Dockerfile`, `.github/`, and documentation.

Create one worktree per contributor from the latest `main`, and keep each worktree on its own branch. For example:

```sh
git fetch origin
git worktree add ../FindMyPacks-backend -b feat/backend origin/main
```

Use corresponding distinct directory and branch names for the other contributors. Integrate changes through pull requests; never commit directly to the upstream `main`. For contributions from a fork, keep the fork's `main` synchronized with the upstream `main` and use the fork's `main` as the source of pull requests targeting upstream `main`. Every commit included in the PR must have a verified SSH signature; the required `ssh-signatures` check enforces this. Do not merge a topic PR into the fork's `main` using GitHub's merge button, because GitHub-generated merge commits are unsigned; integrate signed commits locally and push them to the fork instead. GitHub rebase-and-merge may rewrite commits on the upstream `main`, so signature validation is enforced on PR commits rather than on the resulting upstream history. The repository requires a linear history, the `compose-tests` and `ssh-signatures` checks, and two approvals. The repository's pull request template is in `.github/PULL_REQUEST_TEMPLATE.md`.

## Deployment

No deployment workflow is configured yet because a deployment target has not been selected. The pull-request CI runs through Docker Compose and verifies both tests and the complete application image.
