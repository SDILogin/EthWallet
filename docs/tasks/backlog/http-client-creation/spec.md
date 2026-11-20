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

## Result

### Measurement

- No profiler was used. The time is from a `System.nanoTime` harness.
- Host: a Docker container. It is x86_64 Linux on Apple Silicon with CPU translation. JDK: OpenJDK 17.0.21.
- These are container numbers. They are not from target hardware. The translation makes them slower.
- The harness calls `createHttpClient(OkHttpEngine(OkHttpConfig()))` from the Desktop classpath. Each sample is a new JVM.
- Cold first call, 10 runs: 776–1088 ms with logging off, 748–1205 ms with logging on.
- First call with `Json` and coroutine dispatchers loaded before: 380–683 ms, 7 runs.
- Second call in the same JVM: 1–3 ms. Class loading is the cost.
- About 700 classes load in the first call. This count does not depend on the host.
- On Desktop the first call opens `/dev/urandom` for `SecureRandom`. This is file access.
- Android was not measured. No device was available.

### Decision

- The real time is less than 16 ms only if the container is about 24 to 75 times slower. This is not likely.
- So the client is created off the main thread on Android and Desktop. Android uses the same code path.
- Koin locks each `single` while it creates it. A warm-up in the background does not help if Main asks for the client first.

### Change

- `HttpClientProvider` has one function: `suspend fun provide(): HttpClient`. The caller must be in a coroutine.
- `HttpClientProviderLazy` creates the client on `Dispatchers.IO` at the first `provide()` call. Later calls return the same client.
- Koin binds `HttpClientProvider` on Android and Desktop. Koin does not bind `HttpClient`.
- `JsonRpcTransportKtor`, `FiatRateProviderCoinbase` and `TransfersHistoryProviderBlockscout` get the client from `provide()`.
- The first network request is slower by the creation time. This time is not on Main.
