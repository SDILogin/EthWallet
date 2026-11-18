# Main thread findings

Static review only. Nothing was measured. Each item is a candidate. Severity is an estimate from the code.

- High: likely more than 16 ms on a frame, or a risk of ANR.
- Medium: grows with data size or runs often.
- Low: likely less than 1 ms, or one time at startup.

`REVIEW.md` rule: "suspend function must be safe to call from main thread".

## Candidates

1. QR code encode and draw on Main. Both platforms. Medium to high.
   - `shared/src/androidMain/kotlin/me/sdi/ethwallet/ui/receive/QrCodeCanvas.kt:35`
   - `shared/src/desktopMain/kotlin/me/sdi/ethwallet/ui/receive/QrCodeCanvas.kt:36`
   - `QRCodeWriter().encode` runs in `LaunchedEffect(qrData)`. `LaunchedEffect` runs on the UI dispatcher.
   - It runs again for each distinct valid amount that the user types on the Receive screen.
   - The matrix is 150 or 300 px, not the module grid. The `Canvas` loop checks 22,500 or 90,000 cells and calls one
     `drawRect` for each dark cell on each draw.
2. History list is mapped on Main for each DB change. Both platforms. Medium. It grows with each loaded page.
   - `shared/src/commonMain/kotlin/me/sdi/ethwallet/core/history/TransferHistoryRepository.kt:34`: `.map` without
     `flowOn`.
   - `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/history/HistoryViewModel.kt:75`: `combine` maps each row to
     `HistoryRow` and formats amounts with `BigDecimal`.
   - Room emits off Main. The `map` and `combine` lambdas run in the collector context, which is
     `viewModelScope` (Main).
   - The whole list is mapped again on each upsert during refresh.
3. History date grouping in composition. Both platforms. Medium.
   - `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/history/HistoryScreen.kt:277`: `remember(rows, timeZone.id)`
     calls `historyDateKey` for each row. `historyDateKey` creates a new `SimpleDateFormat` for each call.
   - `HistoryScreen.kt:286-289`: `Calendar.getInstance` and two `historyDateKey` calls on each recomposition,
     outside `remember`.
   - `HistoryScreen.kt:444`: `formatHistoryTime` creates a `DateFormat` for each visible item.
4. History refresh and load more decode and map pages on Main. Both platforms. Medium. Breaks the `REVIEW.md` rule.
   - `HistoryViewModel` calls `HistoryUpdateInteractor.refresh` and `loadMore` from `viewModelScope`.
   - `HistoryUpdateInteractor.kt:29` reads and maps the full cached table.
   - `TransfersHistoryProviderBlockscout.kt:126` decodes each page with `body()`. `TransfersHistoryProviderBlockscout.kt:62`
     decodes one more transaction for each outgoing token transfer.
   - Ktor 3.6.0 `ContentNegotiation` and `KotlinxSerializationConverter` do not change the dispatcher. The class
     strings in `ktor-client-content-negotiation-jvm`, `ktor-serialization-jvm` and
     `ktor-serialization-kotlinx-jvm` contain no `Dispatchers` or `withContext`. So JSON decoding runs on the
     caller dispatcher.
   - None of the interactor, repository, or provider functions sets a dispatcher.
5. Ktor `LogLevel.ALL` in all builds. Both platforms. Low to medium.
   - `shared/src/commonMain/kotlin/me/sdi/ethwallet/di/KoinModule.kt:288`.
   - Ktor builds the full body text of each request and response, also in release builds where `Logger` is
     disabled. This includes the large history pages.
   - It is also a privacy risk: the bodies contain addresses and signed transactions.
6. Biometric availability check outside `withContext`. Android. Low. Breaks the `REVIEW.md` rule.
   - `shared/src/androidMain/kotlin/me/sdi/ethwallet/core/security/AndroidBiometricUnlock.kt:91`:
     `canAuthenticate` is a binder call.
   - Called on Main from `VerifyPinCodeViewModel`, `SettingsViewModel` and `SetPinCodeViewModel`.
7. Boot count read outside `withContext`. Android. Low. Breaks the `REVIEW.md` rule.
   - `shared/src/androidMain/kotlin/me/sdi/ethwallet/core/security/KeyStorePinAttemptsGuard.kt:43`:
     `Settings.Global.getInt(BOOT_COUNT)` is a `ContentResolver` call.
   - Called from `freshState()`, `normalize()` and `onFailedAttempt()` outside `withContext(dispatcher)`.
8. Disk access when Koin creates singletons on the UI thread. Desktop. Low, one time.
   - `shared/src/commonMain/kotlin/me/sdi/ethwallet/core/security/PinArtifactsDataStore.kt:31`:
     `Files.createDirectories`.
   - `shared/src/desktopMain/kotlin/me/sdi/ethwallet/database/DesktopDatabaseBuilder.kt:10`: `mkdirs`.
   - Koin resolves them from composition or `LaunchedEffect` on the AWT event thread.
9. `HttpClient` creation on Main at first injection. Both platforms. Low, one time.
   - `AndroidKoinModule.kt:65`, `DesktopKoinModule.kt:67`, `KoinModule.kt:271`: OkHttp engine and JSON setup.
10. `ReceiveQrViewModel` starts flows in `init`. Both platforms. Low. Breaks the `REVIEW.md` rule "ViewModel `init`
    must be fast".
    - `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/receive/ReceiveQrViewModel.kt:70`.

## Minor, no action

- `MainActivity.kt`: `DatabaseBuilder.getInstance()` builds Room. It does not open the DB.
- `ImportWalletViewModel.isValidMnemonic`: one regex split for each keystroke.
- `JsonRpcTransportKtor.execute`: reads small RPC responses in the caller context.

## Off Main, but the dispatcher is not on the lowest level

- `WalletsListViewModel` injects `Dispatchers.IO` on the ViewModel level.
- `CreateWalletViewModel.kt:51` uses `withContext(NonCancellable + Dispatchers.IO)`.
- `EthTransferInteractor` wraps a call that already switches to `Dispatchers.Default`.

## Checked as correct

- `ethwallet-kotlin`: `createWallet`, `Mnemonic.parse`, `derivePrivateKey`, `deriveAddress` and `signTransaction`
  call Rust inside `withContext(Dispatchers.Default)`.
- Desktop crypto: Secure Enclave (IO), Argon2 (Default), zxcvbn (Default), `KekCipher` (Default).
- Android crypto: PBKDF2 in `PinCodeStorageDataStore` (Default), `KeyStoreEncryptor`,
  `MasterKekStorageAndroidKeyStore`, and the load and write paths of `KeyStorePinAttemptsGuard`.
- Data cleaners: Room, DataStore and KeyStore on IO.
- Room DAOs: only suspend and `Flow` methods. Desktop Room uses `setQueryCoroutineContext(Dispatchers.IO)`.
- DataStore: only `data.first()` and `edit`.
- Lottie: compottie reads and parses the JSON files on its IO dispatcher.
- No `runBlocking`, `Thread.sleep` or `Future.get` in production code.

## Tools

- StrictMode: Android debug builds enable the thread and VM policies with `detectAll()` and `penaltyLog()` in
  `EthWalletApplication`. The thread policy finds disk and network access. It does not find CPU work, so
  candidates 1 to 4 need the CPU profiler.
- Android profiler: the manifest has `<profileable android:shell="true" />`. No change is necessary.

## Manual steps

These checks need a device or a Mac. They were not done.

### StrictMode on Android

1. `./gradlew :app:installDebug`
2. `adb logcat -c && adb logcat -s StrictMode:D`
3. Do these flows: onboarding and PIN setup, PIN unlock, biometric unlock, wallets list, Receive with an amount,
   History refresh and load more, Send, Settings.
4. Record each `StrictMode policy violation` with its stack trace. The VM policy can also log untagged socket and
   non-SDK API use from libraries.

### CPU profile on Android

1. Open the project in Android Studio. Run the `app` debug variant on a device.
2. Open Profiler, select the app process, and record a System Trace (or Java/Kotlin method sample).
3. Do the flows from the StrictMode steps.
4. On the `main` thread, find slices longer than 16 ms. Compare them with candidates 1 to 4.
5. A debug build is slower than a release build. Use the times to compare, not as absolute values. The release
   build has no signing config, so it cannot be installed as is.

### CPU profile on Desktop (Apple Silicon Mac)

1. `./gradlew :desktopApp:run`
2. `jcmd | grep me.sdi.ethwallet.desktop.MainKt` to find the PID.
3. `jcmd <pid> JFR.start name=main settings=profile`
4. Do the flows from the StrictMode steps.
5. `jcmd <pid> JFR.dump name=main filename=ethwallet.jfr`
6. Open `ethwallet.jfr` in JDK Mission Control. Filter the method profile by thread `AWT-EventQueue-0`.
