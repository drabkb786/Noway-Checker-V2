# NOWAY Response Checker

**Owner:** Abdul Basit  
**Android package:** `com.noway.responsechecker`

NOWAY Response Checker is a focused Android HTTP diagnostics app for checking systems you own or are authorized to test. It supports single-target inspection and persistent bulk queues, while keeping history locally on the device.

## Features

- Password-gated console (the password is verified using a SHA-256 digest; the raw password is not stored in source).
- Unique dark "network pulse" dashboard with Success / Redirected / Responded / Failed classification.
- Single response checker for domain, IP or URL targets.
- GET, HEAD and POST UI presets plus custom request headers and body.
- Response status, message, headers and a bounded 64 KiB body preview.
- Redirect following plus full redirect chain and final destination.
- DNS resolution with resolved IPv4/IPv6 addresses and DNS timing.
- HTTPS/TLS protocol, cipher and certificate subject/issuer information.
- CDN Lens with evidence-based Cloudflare and Amazon CloudFront detection; also basic Fastly/Akamai hints.
- Bulk input from pasted text, CSV-like lines, or imported files; duplicate removal included.
- Persistent bulk execution through Android WorkManager with pause/resume/cancel state.
- Six-way parallel bulk checking inside the persistent WorkManager queue, designed to make large lists substantially faster while keeping a conservative mobile default.
- Local SQLite history and separate response categories.
- CSV export of stored results.

## Result model

- **SUCCESS** — 2xx response.
- **REDIRECTED** — redirect occurred or final response is 3xx.
- **RESPONDED** — the server responded but with a non-2xx/non-3xx status such as 4xx/5xx.
- **FAILED** — DNS, TLS, timeout, connection or other transport failure.

This separation matters: a `403` proves a server responded, but it is not the same as a successful `200`.

## Cloudflare / CloudFront detection

CDN identification is intentionally evidence-based rather than absolute. Examples:

- Cloudflare: `CF-Ray`, `CF-Cache-Status`, `Server: cloudflare`.
- CloudFront: `X-Amz-Cf-Id`, `X-Amz-Cf-Pop`, CloudFront evidence in `X-Cache`, `Via`, or `Server`.

A provider label therefore describes observed response-header evidence, not ownership proof.

## Build

Current project configuration:

- JDK 17
- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- compileSdk / targetSdk 36
- Jetpack Compose BOM 2026.06.00
- WorkManager 2.11.2
- OkHttp 5.3.0

Open the project in a compatible Android Studio release and sync, or use Gradle 9.5:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

GitHub Actions runs both commands and uploads the debug APK as a workflow artifact after a successful main-branch build.

## Privacy & scope

History is stored locally in `noway.db`. The app does not require a cloud account. Use the checker only against domains, IPs and services you own or are authorized to test.
