# Validation Run

Use this skill when you need the narrowest reliable validation for a change in this repository.

## Goal

Validate only the affected slice first, then expand only if needed.

## Default validation

Run tests with local Gradle state:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
$env:Path='C:\Program Files\Java\jdk-22\bin;' + $env:Path
New-Item -ItemType Directory -Force '.gradle-user-home' | Out-Null
$env:GRADLE_USER_HOME=(Resolve-Path '.gradle-user-home')
./gradlew.bat test
```

## When to use lighter validation

- Docs-only changes: read the updated files and verify command/path accuracy.
- Dev workflow changes: run the relevant script or verify the referenced script and commands.
- Export path changes: use the export smoke flow.

## Escalate validation only when needed

- parser/integration changes: run the smallest relevant tests first, then confirm startup/config assumptions
- API/service changes in the dev slice: use export smoke if the touched flow is documents/export related

## Validation rule

Do not claim a flow was tested unless the relevant command or script actually ran.
