# Export Smoke Flow

Use this skill when a change could affect the supported `documents -> export -> file` slice.

## Goal

Quickly prove that the main local operator flow still works.

## Fast path

If the app is already running:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev-export-smoke.ps1
```

This script:

- checks health
- fetches demo documents
- selects the first document
- creates a JSON export
- downloads the export into `exports\dev\manual`

## Manual path

1. `GET /actuator/health`
2. `GET /api/v1/documents`
3. `POST /api/v1/exports`
4. `GET /api/v1/exports/{jobId}/file`

## What to verify

- health is `UP`
- at least one demo document is returned
- export job status is `COMPLETED`
- downloaded file exists on disk

## Use this after

- export service changes
- document query changes
- auth/config changes affecting the dev slice
- bootstrap or README workflow updates
