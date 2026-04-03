# gaime-bridge AGENTS

## Repository shape

- `src/main/java/com/dynorix/gaimebridge/web`: HTTP and UI entry points only.
- `src/main/java/com/dynorix/gaimebridge/service`: Core Service layer and orchestration.
- `src/main/java/com/dynorix/gaimebridge/integration/taxportal`: parser and browser automation adapters.
- `src/main/java/com/dynorix/gaimebridge/repository`: persistence access only.
- `src/main/java/com/dynorix/gaimebridge/domain`: entities and enums.
- `src/main/resources/static/ui`: operator UI assets.
- `scripts`: local developer workflows.
- `docs/architecture.md`: intended architecture and layering.

## Main entry points

- App: `src/main/java/com/dynorix/gaimebridge/GaimeBridgeApplication.java`
- HTTP/API: controllers under `web`
- Local UI: `/ui/operator.html`
- Primary dev flow: demo documents -> JSON export -> file download

## Architecture rules

- Business logic belongs in the Core Service layer only.
- Controllers and UI are interfaces only: mapping, validation, auth context, response shaping.
- Parser code stays isolated under `integration/taxportal` and is accessed only through Core Service paths.
- Export stays independent of parser internals.
- Repositories do storage access only, never orchestration.

Forbidden:

- controller -> repository
- controller/API -> parser
- UI -> DB
- business rules in controllers, DTOs, config, or parser adapters

## Build, run, validate

- Tests: `./gradlew.bat test`
- Dev app: `./gradlew.bat bootRun --args="--spring.profiles.active=dev"`
- Assisted bootstrap: `powershell -ExecutionPolicy Bypass -File .\scripts\dev-first-run.ps1`
- Export smoke against a running app: `powershell -ExecutionPolicy Bypass -File .\scripts\dev-export-smoke.ps1`
- Live portal bootstrap: `powershell -ExecutionPolicy Bypass -File .\scripts\start-live-asan.ps1`

Notes:

- Gradle toolchain targets Java 21.
- In sandboxed runs, keep `GRADLE_USER_HOME` inside the workspace.
- The supported first-run slice is `dev` with H2, demo data, and JSON export.

## Risky areas

- `integration/taxportal`: portal selectors, browser automation, parsing assumptions
- `SyncOrchestratorServiceImpl`: cross-boundary orchestration and persistence
- `ExportServiceImpl`: file output path and job lifecycle
- config/profile behavior: `application.yml` plus `application-dev.yml`

## Safe-change policy

- Inspect the touched workflow before editing.
- Prefer the smallest high-value change.
- Do one improvement at a time.
- Prefer scripts, tests, and docs before refactors.
- If logic is already outside Core Service, do not widen the problem; propose larger cleanup separately.
- Stop and explain if a change would spread across multiple modules or weaken architecture boundaries.

## Refactoring rules

- Refactor only to fix a real issue, remove meaningful duplication, or reduce repeated operator/developer friction.
- Avoid cosmetic moves and package churn.
- Keep exports, parser code, and transport code separate.

## Done means

- The change respects the Core/UI/API/parser/export boundaries.
- The narrowest relevant validation has been run.
- Docs or skills are updated if the developer workflow changed.
- New guidance points to real repo paths and real commands.

## Repo skills

- `.agents/skills/architecture-guard/SKILL.md`
- `.agents/skills/dev-bootstrap/SKILL.md`
- `.agents/skills/validation-run/SKILL.md`
- `.agents/skills/export-smoke-flow/SKILL.md`
- `.agents/skills/parser-safe-change/SKILL.md`
