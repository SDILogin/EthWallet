# Task

#10 Find long-running main thread operations; set up profiler and StrictMode

Profile the app, enable StrictMode for development, and identify long-running operations on the main thread.

## Scope

- Enable StrictMode in Android debug builds only. The thread and VM policies use `detectAll()` and `penaltyLog()`.
  Violations are logged. They do not crash the app.
- The Android profiler needs no change: the manifest already has `<profileable android:shell="true" />`.
- Review production code statically for blocking or CPU work on the main thread on Android and Desktop. Check
  suspend functions and dispatchers against the `REVIEW.md` rule "suspend function must be safe to call from main
  thread". Nothing was measured.
- Record each finding as a backlog task spec (see "Follow-up tasks").
- Add the rule "all disk access runs on `Dispatchers.IO`" to `REVIEW.md`.
- Add the rule "do not write code that violates StrictMode" to `CLAUDE.md`.
- Fix library StrictMode violations. The Android Ktor OkHttp engine uses the app's `StatsTaggedSocketFactory`.
  Without it, the VM policy reports each OkHttp socket as untagged. Android tags a socket when it creates the socket
  file descriptor. OkHttp calls `setSoTimeout` before `connect`, and that call creates the file descriptor. So the
  factory sets the thread tag and creates the file descriptor in `createSocket()`.
- Other library violations (for example non-SDK API use) need a device run to find. They are not known yet.

The StrictMode thread policy finds disk and network access. It does not find CPU work. CPU findings need the
profiler.

## Out of scope

- Fixes for the main thread findings.
- A Desktop StrictMode equivalent, macrobenchmark or baseline profile modules, iOS.

## Follow-up tasks

- `docs/tasks/backlog/receive-qr-encode-off-main/spec.md`
- `docs/tasks/backlog/history-off-main/spec.md`
- `docs/tasks/backlog/history-date-formatting/spec.md`
- `docs/tasks/done/ktor-logging-level/spec.md`
- `docs/tasks/done/android-binder-calls-off-main/spec.md`
- `docs/tasks/done/disk-access-on-io/spec.md`
- `docs/tasks/backlog/http-client-creation/spec.md`
- `docs/tasks/done/receive-qr-viewmodel-start/spec.md`
- `docs/tasks/done/dispatcher-lowest-level/spec.md`

## Checked as correct

- `ethwallet-kotlin`: `createWallet`, `Mnemonic.parse`, `derivePrivateKey`, `deriveAddress` and `signTransaction`
  call Rust inside `withContext(Dispatchers.Default)`.
  The first call on Desktop also extracts the native library on `Dispatchers.Default`. See
  `docs/tasks/done/disk-access-on-io/spec.md`.
- Desktop crypto: Secure Enclave (IO), Argon2 (Default), zxcvbn (Default), `KekCipher` (Default).
- Android crypto: PBKDF2 in `PinCodeStorageDataStore` (Default), `KeyStoreEncryptor`,
  `MasterKekStorageAndroidKeyStore`, and the load and write paths of `KeyStorePinAttemptsGuard`.
- Data cleaners: Room, DataStore and KeyStore on IO.
- Room DAOs: only suspend and `Flow` methods. Desktop Room uses `setQueryCoroutineContext(Dispatchers.IO)`.
- DataStore: only `data.first()` and `edit`.
- Lottie: compottie reads and parses the JSON files on its IO dispatcher.
- No `runBlocking`, `Thread.sleep` or `Future.get` in production code.
- Small work on Main, no action: `MainActivity` builds Room but does not open the DB.
  `ImportWalletViewModel.isValidMnemonic` does one regex split for each keystroke.
  `JsonRpcTransportKtor.execute` reads small RPC responses in the caller context.

## Manual steps

These checks need a device or a Mac. They were not done.

### StrictMode on Android

1. `./gradlew :app:installDebug`
2. `adb logcat -c && adb logcat -s StrictMode:D`
3. Do these flows: onboarding and PIN setup, PIN unlock, biometric unlock, wallets list, Receive with an amount,
   History refresh and load more, Send, Settings.
4. Record each `StrictMode policy violation` with its stack trace. Library violations must be fixed too. An
   `UntaggedSocketViolation` with OkHttp in the stack trace means that `StatsTaggedSocketFactory` does not work.

### CPU profile on Android

1. Open the project in Android Studio. Run the `app` debug variant on a device.
2. Open Profiler, select the app process, and record a System Trace (or Java/Kotlin method sample).
3. Do the flows from the StrictMode steps.
4. On the `main` thread, find slices longer than 16 ms. Compare them with the QR code and History follow-up tasks.
5. A debug build is slower than a release build. Use the times to compare, not as absolute values. The release
   build has no signing config, so it cannot be installed as is.

### CPU profile on Desktop (Apple Silicon Mac)

1. `./gradlew :desktopApp:run`
2. `jcmd | grep me.sdi.ethwallet.desktop.MainKt` to find the PID.
3. `jcmd <pid> JFR.start name=main settings=profile`
4. Do the flows from the StrictMode steps.
5. `jcmd <pid> JFR.dump name=main filename=ethwallet.jfr`
6. Open `ethwallet.jfr` in JDK Mission Control. Filter the method profile by thread `AWT-EventQueue-0`.

## Definition of done

- A debug build enables the StrictMode thread and VM policies with `penaltyLog`. A release build does not.
- Each finding has a backlog spec.
- `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest` and `spotlessApply` pass.
