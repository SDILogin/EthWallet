# Task

#332 Measure HttpClient creation on the main thread

## Background

Koin creates the Ktor `HttpClient` with the OkHttp engine and its JSON setup on Main at first injection.

- `shared/src/androidMain/kotlin/me/sdi/ethwallet/di/AndroidKoinModule.kt:65`
- `shared/src/desktopMain/kotlin/me/sdi/ethwallet/di/DesktopKoinModule.kt:67`
- `shared/src/commonMain/kotlin/me/sdi/ethwallet/di/KoinModule.kt:271`

Estimated severity: low, one time at startup. Platforms: Android and Desktop.

Found in GitHub PR #27 for #10 Find long-running main thread operations; set up profiler and StrictMode.
Static review only. Nothing was measured. Severity: high is more than 16 ms on a frame. Medium grows with data size
or runs often. Low is a few ms or less, or one time at startup.

## Scope

- Measure the creation time on an Android device and on Desktop with the profiler.
- If it takes more than 16 ms, create the client off the main thread.
- If it takes less, record the result in this spec. Make no code change.

## Definition of done

- The measured time is recorded in this spec.
- If code changed, `./gradlew spotlessApply` and the shared tests pass.
