# Task

#329 Limit Ktor HTTP logging to debug builds

## Background

`shared/src/commonMain/kotlin/me/sdi/ethwallet/di/KoinModule.kt:288` sets `LogLevel.ALL` in all builds. Ktor builds
the full body text of each request and response, also in release builds where `Logger` is disabled. This includes
the large history pages.

Debug builds print these bodies to logcat. They contain addresses and signed transactions.

Estimated severity: low to medium. Platforms: Android and Desktop.

Found in PR #27 (#10 Find long-running main thread operations; set up profiler and StrictMode). Static review only. Nothing was measured.

## Scope

- Release builds do not install HTTP logging, or use `LogLevel.NONE`.
- Debug builds keep the current logging.

## Definition of done

- A release build does not build HTTP log text.
- `./gradlew spotlessApply` and the shared tests pass.
