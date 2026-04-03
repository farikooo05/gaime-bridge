# Dynorix Gaime Bridge

This project now has one official starting path:

1. run the app in `dev`
2. view demo documents
3. create JSON export
4. download the file

The local app now uses PostgreSQL for `dev` runs so migrations and runtime behavior stay aligned with the real stack.

## Official start path

### 1. Start the app

Fastest path:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev-first-run.ps1
```

This helper:

- expects JDK 21, matching the repository toolchain
- reuses a healthy local app if one is already running
- otherwise starts `bootRun` with the `dev` profile
- waits for health
- runs the existing documents -> export -> download smoke flow
- leaves the app running for local debugging

Manual path:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
./gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

What `dev` means in this project:

- it uses a local PostgreSQL database
- it seeds demo documents automatically
- it uses simple local API credentials
- it is the easiest mode for debugging the first vertical slice

### 2. Check health

```powershell
curl.exe http://localhost:8080/actuator/health
```

Expected result: `UP`

### 3. List demo documents

```powershell
curl.exe -u dev:dev123 "http://localhost:8080/api/v1/documents"
```

Expected result: a non-empty JSON page with demo documents.

### 4. Create a JSON export

Take one `id` from the documents response and call:

```powershell
curl.exe -u dev:dev123 -X POST "http://localhost:8080/api/v1/exports" -H "Content-Type: application/json" -d "{\"format\":\"JSON\",\"documentIds\":[\"PASTE_DOCUMENT_ID_HERE\"]}"
```

Expected result: export job with status `COMPLETED`.

### 5. Download the file

Take the export job `id` from the previous response and call:

```powershell
curl.exe -u dev:dev123 "http://localhost:8080/api/v1/exports/{jobId}/file" --output export.json
```

Expected result: `export.json` appears in your current folder.

### Browser operator page

If you want to use the same flow in a browser instead of `curl`, open:

```text
http://localhost:8080/ui/operator.html
```

This page lets you:

- connect with local API credentials
- browse demo documents
- preview details
- select one or more documents
- export JSON
- download the generated file

## Optional helper script

If you want one assisted smoke flow after the app is already running, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev-export-smoke.ps1
```

This script:

- checks health
- fetches demo documents
- takes the first document automatically
- creates a JSON export
- downloads the export file into `.\exports\dev\manual`

## Local credentials

- username: `dev`
- password: `dev123`

## Useful endpoints for the first phase

- `GET /`
- `GET /actuator/health`
- `GET /api/v1/documents`
- `GET /api/v1/documents/{id}`
- `GET /api/v1/documents/by-number/{number}`
- `POST /api/v1/exports`
- `GET /api/v1/exports/{jobId}`
- `GET /api/v1/exports/{jobId}/file`

## Temporary Live Asan Config

The project now also contains a temporary live-integration path for the real e-Taxes portal.

This mode is for proving the first real end-to-end flow:

1. open the real Asan login page
2. submit phone and user ID
3. confirm the verification on the phone
4. select the legal taxpayer
5. reuse that authenticated browser session to load real invoice JSON

This is intentionally not the final user-facing login UX yet. It is a technical bridge so we can verify live ingestion before we build the proper login flow inside our own app.

To prepare it:

1. copy `.env.example` to `.env` if needed
2. fill the `TAX_PORTAL_*` variables for your real Asan account
3. keep `TAX_PORTAL_HEADLESS=false` so the verification flow is visible
4. set `TAX_PORTAL_LEGAL_TIN` to the company you want to enter after verification

For local runs, note that Spring Boot does not read `.env` automatically in this project.
Use the helper script instead:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-live-asan.ps1
```

This script:

- loads `.env` into the process environment
- forces `APP_DEMO_ENABLED=false`
- forces `TAX_PORTAL_BROWSER_ENABLED=true`
- keeps the browser visible for the phone verification step
- starts the app with the `dev` profile so the first live run stays simple

Important notes:

- `TAX_PORTAL_USERNAME` currently means the Asan phone number without `+994`
- `TAX_PORTAL_PASSWORD` currently means the Asan `userId`
- after submit, the app waits on the verification page until the phone confirmation succeeds
- after verification, the app selects the legal taxpayer via `TAX_PORTAL_LEGAL_TIN`

Some live portal paths are already confirmed:

- login: `/eportal/login/asan`
- verification page: `/eportal/verification/asan`
- verification start: `/api/po/auth/public/v1/asanImza/start`
- verification status: `/api/po/auth/public/v1/asanImza/status`
- certificates: `/api/po/auth/public/v1/asanImza/certificates`
- choose taxpayer: `/api/po/auth/public/v1/asanImza/chooseTaxpayer`
- invoices outbox: `/api/po/invoice/public/v2/invoice/find.outbox`
- invoices inbox: `/api/po/invoice/public/v2/invoice/find.inbox`
- invoices draft: `/api/po/invoice/public/v2/invoice/find.draft`

Some values still need to be finalized from live DevTools data before the first real sync:

- `TAX_PORTAL_HOME_SUCCESS_SELECTOR`
- exact path templates for versions/history/tree if the current defaults differ from the live endpoints

## Local database

For local `bootRun`, make sure PostgreSQL is available on `localhost:${POSTGRES_PORT}`.

If you use the repository Docker setup, start only the database container:

```powershell
docker compose up -d postgres
```

The local scripts override the Docker-only hostname from `.env` and connect to:

- `jdbc:postgresql://localhost:${POSTGRES_PORT}/gaime_bridge`

Tests still use H2 in-memory for fast isolated execution.
