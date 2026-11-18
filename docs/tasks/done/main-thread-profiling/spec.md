# Task

#10 Find long-running main thread operations; set up profiler and StrictMode

Profile the app, enable StrictMode for development, and identify long-running operations on the main thread.

## Scope

- Enable StrictMode in Android debug builds only. Violations are logged. They do not crash the app.
- The Android profiler needs no change: the manifest already has `<profileable android:shell="true" />`.
- Review production code statically for blocking or CPU work on the main thread on Android and Desktop. Check
  suspend functions and dispatchers against the `REVIEW.md` rule "suspend function must be safe to call from main
  thread".
- Record each candidate with its location, platform, and estimated severity in `findings.md` in this folder.
- Record the device, profiler, and Mac checks as manual steps in `findings.md`.

## Out of scope

- Fixes for the candidates. The owner decides which become tasks.
- A Desktop StrictMode equivalent, macrobenchmark or baseline profile modules, iOS.

## Definition of done

- A debug build enables the StrictMode thread and VM policies with `penaltyLog`. A release build does not.
- `findings.md` lists the candidates, the areas checked as correct, and the manual steps.
- `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest` and `spotlessApply` pass.
