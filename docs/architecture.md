# Dynorix Gaimə Bridge MVP

## 1. Practical architecture

The project should be built as an integration-first service, not as a UI-first desktop utility.

Target modules:

- `api`: REST controllers, request validation, response contracts.
- `application`: use cases, orchestration, sync/export job lifecycle.
- `domain`: entities, enums, domain invariants, value objects.
- `integration.taxportal`: isolated browser automation and parsing adapters.
- `infrastructure.persistence`: JPA repositories, specifications, migrations.
- `infrastructure.storage`: export files, snapshots, attachments.
- `observability`: structured logging, correlation IDs, audit events.

Layering:

1. `api` talks only to `application`.
2. `application` owns orchestration and depends on ports.
3. `integration.taxportal` implements parser ports.
4. `domain` stays free of UI/parser details.
5. `infrastructure` supports the application layer, but does not define business rules.

## 2. Main entities

- `TaxDocument`: canonical e-Qaimə header and aggregate root.
- `TaxDocumentLine`: line items parsed from document details.
- `Attachment`: downloadable print forms or related files.
- `TaxSession`: tax portal session lifecycle and diagnostics.
- `SyncJob`: asynchronous import/synchronization lifecycle.
- `ExportJob`: export lifecycle and output file tracking.
- `IntegrationLog`: audit, parser, API and export events.

## 3. Database structure

Core tables:

- `tax_documents`
- `tax_document_lines`
- `attachments`
- `tax_sessions`
- `sync_jobs`
- `export_jobs`
- `integration_logs`

Important database rules:

- `tax_documents.external_document_id` should be unique whenever present.
- Composite indexes are required for `document_date`, `document_number`, `status`, `seller_tax_id`, `buyer_tax_id`.
- Large diagnostic payloads must be retained with policy, not forever.
- Exports, HTML snapshots and screenshots belong in file/object storage, not inside PostgreSQL blobs.

## 4. Stack recommendation

Use `Playwright for Java` over Selenium for this MVP.

Why Playwright:

- better waiting model and fewer flaky sleeps;
- stronger tooling for network interception, downloads and screenshots;
- easier browser context isolation for session management;
- better diagnostics when the tax portal changes behavior;
- a cleaner fit for modern JS-heavy portals.

When Selenium still makes sense:

- if the deployment environment already standardizes on Selenium Grid;
- if there is an existing QA/automation estate built around WebDriver.

For a greenfield parser with unstable UI, Playwright is the stronger default.

## 5. What is impractical in the current TZ

- The stated MVP is too wide if UI, API, exports and resilient parsing are all treated as first-class deliverables in one iteration.
- "The parser must not break when the site changes" is not realistic. The real requirement is diagnosable, localized breakage with quick repair.
- Desktop-or-web UI is not a small detail; it is a product decision. For MVP, backend + web UI is the practical path.
- JSON should be the canonical internal representation. XML/XLSX/CSV should be projections, not independent truth sources.

## 6. Recommended delivery phases

Phase 1:

- login/session management;
- list page parsing;
- detail page parsing;
- persistence in PostgreSQL;
- REST read API;
- JSON export;
- audit and parser diagnostics.

Phase 2:

- batch sync and export jobs;
- file storage abstraction;
- XML/XLSX/CSV exports;
- richer filters and operator UI.

Phase 3:

- external ERP integration hardening;
- scheduler and recurring sync;
- multi-tenant/user model if the business actually needs it.
