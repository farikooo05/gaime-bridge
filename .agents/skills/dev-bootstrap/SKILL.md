# Dev Bootstrap

Use this skill when you need to start the supported local development slice for gaime-bridge as quickly and safely as possible.

## Goal

Bring up the `dev` profile, confirm health, and make the primary vertical slice available for debugging.

## Preferred path

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev-first-run.ps1
```

What it does:

- checks for Java 21
- starts or reuses the local `dev` app
- waits for health
- runs the export smoke flow

## Manual fallback

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
./gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

Then check:

```powershell
curl.exe http://localhost:8080/actuator/health
```

Expected result: `UP`

## Notes

- The `dev` profile uses PostgreSQL, demo data, and local API credentials.
- If bootstrap fails, inspect `.run/dev-boot.out.log` and `.run/dev-boot.err.log`.
- Do not use the live Asan script for routine local validation.
