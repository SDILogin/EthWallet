# Task

#331 Move disk access to the IO dispatcher

## Background

`REVIEW.md` requires that all disk access runs on `Dispatchers.IO`. These places do not follow it:

- `shared/src/commonMain/kotlin/me/sdi/ethwallet/core/security/PinArtifactsDataStore.kt:31`:
  `Files.createDirectories` and `Files.setPosixFilePermissions`. Desktop Koin creates this singleton on the AWT
  event thread.
- `shared/src/desktopMain/kotlin/me/sdi/ethwallet/database/DesktopDatabaseBuilder.kt:10`: `mkdirs`. Desktop Koin
  creates this singleton on the AWT event thread.
- `ethwallet-kotlin/src/jvmMain/kotlin/me/sdi/ethwallet/kotlin/NativeCrypto.jvm.kt:110`: the native library is
  copied to a temporary directory. This runs on `Dispatchers.Default`, because the public API wraps the first native
  call in `withContext(Dispatchers.Default)`.

Estimated severity: low, one time at startup. Platform: Desktop.

Found in GitHub PR #27 for #10 Find long-running main thread operations; set up profiler and StrictMode.
Static review only. Nothing was measured. Severity: high is more than 16 ms on a frame. Medium grows with data size
or runs often. Low is a few ms or less, or one time at startup.

## Scope

- Run each disk access above on `Dispatchers.IO`.

## Definition of done

- No disk access above runs on the main thread or on `Dispatchers.Default`.
- `./gradlew spotlessApply`, the shared tests and the desktop tests pass.
