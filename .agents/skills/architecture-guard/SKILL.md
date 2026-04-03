# Architecture Guard

Use this skill when a change touches controllers, services, repositories, parser code, export code, DTOs, or config and you need to keep the repository architecture strict.

## Goal

Keep business logic in Core Service and avoid leaking parser or persistence concerns into the interface layer.

## Steps

1. Read `docs/architecture.md`.
2. Read the touched files under `web`, `service`, `integration/taxportal`, and `repository`.
3. Classify the new logic before editing:
   - interface concern
   - business logic
   - parser adapter concern
   - persistence concern
4. Put business logic in `src/main/java/com/dynorix/gaimebridge/service`.
5. Keep `web` classes thin: request mapping, validation, auth context, response return only.
6. Keep parser code inside `integration/taxportal` and reach it only through Core Service orchestration.
7. Keep exports independent from parser implementation details.

## Hard stops

- Do not add controller -> repository calls.
- Do not add controller/API -> parser calls.
- Do not put business rules into DTOs, config classes, or parser adapters.
- Do not let export code depend on parser-only types.

## Review checklist

- Would this logic still make sense if the HTTP layer changed?
- Would this logic still make sense if the parser implementation changed?
- Can this logic live in an existing service instead of a controller or adapter?
- Does this change widen an already imperfect boundary?
