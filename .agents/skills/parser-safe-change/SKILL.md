# Parser Safe Change

Use this skill when touching `src/main/java/com/dynorix/gaimebridge/integration/taxportal` or Core Service code that orchestrates parser behavior.

## Goal

Keep parser changes isolated, diagnosable, and architecture-safe.

## Rules

- Keep portal selectors, page parsing, and browser automation inside `integration/taxportal`.
- Reach parser behavior through `TaxPortalClient` or Core Service orchestration, not directly from controllers.
- Preserve raw payload and diagnostic capture paths when changing extraction logic.
- Do not mix export concerns into parser code.

## Change checklist

1. Identify whether the change is selector-level, transport-level, mapping-level, or orchestration-level.
2. Prefer a localized adapter change over a cross-layer refactor.
3. Check `SyncOrchestratorServiceImpl` for any downstream assumptions your change could break.
4. Check `application.yml` and `application-dev.yml` if the change depends on properties or profile behavior.
5. Keep failure modes diagnosable: avoid silent fallbacks that hide parser breakage.

## Hard stops

- Do not call parser classes from controllers.
- Do not move parser behavior into UI/API code.
- Do not make export success depend on parser implementation internals.

## Validate

- Run the narrowest relevant tests first.
- If the change affects the supported local slice, also run the export smoke flow or explain why it is not applicable.
