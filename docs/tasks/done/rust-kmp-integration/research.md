# Research: alternatives for Rust/KMP integration

Task: #28 Research alternatives for Rust/KMP integration (docs/tasks/done/rust-kmp-integration/spec.md).

Evidence tags:

- **[Linux]**: ran in the agent container (Linux x86_64, Docker, cargo 1.97.1, cargo-ndk 4.1.2, NDK
  30.0.14904198) on 2026-11-18.
- **[Source]**: primary source. Link and access date 2026-11-18.
- **[Unverified]**: needs macOS or iOS. See "Manual checks".
- **[Inference]**: reasoning from code or docs, not tested.
- **[Repo]**: read from the repository at commit `0fc6515`. The baseline section uses this tag for all its facts.

In the option sections, a statement without a tag is [Inference].

## Recommendation

1. Now: fix the IDE sync wiring. This is independent and can land first. Step A fixes sync on Linux hosts. The
   value of step B on macOS depends on manual checks 1 and 2. Then move the Rust build logic into a typed
   convention plugin (option 3).
2. Keep the handwritten JNI and C ABI. Decide on UniFFI (option 4) only after #277 Try UniFFI for Rust/KMP
   bindings reports.
3. Do not publish prebuilt artifacts (option 1) until CI with macOS runners exists.
4. Do not adopt a third-party Rust Gradle plugin (option 2). Neither tested plugin works with this build today.
5. Do not narrow to Android only (option 5). A narrower variant, iOS opt-in, is a decision for the owner.

## Baseline: current setup [Repo]

- `ethwallet-kotlin/build.gradle.kts` registers `Exec` tasks:
  - `buildRustAndroid`: `cargo ndk` for `arm64-v8a` and `x86_64`, API 24. The output is added to Android `jniLibs`
    through `KotlinMultiplatformAndroidComponentsExtension` and the typed `RustNativeLibrarySync` task.
  - `buildRustMacosArm64`: `aarch64-apple-darwin` dylib. `packageRustMacosLibraries` copies it to JVM resources.
    `NativeCrypto.jvm.kt` extracts it to a temp directory and loads it.
  - `buildRustIosArm64`, `buildRustIosSimulatorArm64`, `buildRustIosX64`: static libraries for Kotlin/Native
    cinterop.
  - `cargoInputs` declares the manifest, lockfile, toolchain file, `src`, and `include` as inputs.
- Bindings: 6 handwritten JNI exports and a C ABI (`rust/include/ethwallet_crypto.h`, `EWC_ABI_VERSION`). Kotlin
  allocates output buffers and clears them. Rust writes output only on success.
- Duplicated wiring: the Apple Silicon host check, the `buildRustMacosArm64` dependency, and the
  `java.library.path` setup are in three build files:
  - `ethwallet-kotlin/build.gradle.kts` (`testAndroidHostTest`)
  - `shared/build.gradle.kts` (`testAndroidHostTest`)
  - `app/build.gradle.kts` (Sepolia E2E)
- `shared` and `app` hard-code the path `ethwallet-kotlin/build/rust/target/aarch64-apple-darwin/release`.
- Two more Apple Silicon checks for Sepolia E2E are in `app/build.gradle.kts` and `desktopApp/build.gradle.kts`.
- `shared/build.gradle.kts` also has an `Exec` task, `buildSecureEnclaveMacosArm64` (`xcrun swiftc`). It is not
  Rust.
- Consumers: `app` (Android), `desktopApp` through `shared` (macOS JVM). No module and no Xcode project consumes the
  iOS targets. `shared`, `app`, and `desktopApp` declare no iOS targets.
- No CI. The repository has no `.github/workflows`. CLAUDE.md says a local pre-push hook runs checks; in the agent
  container it runs only `spotlessCheck`.
- Reproducibility today:
  - Pinned: Rust toolchain 1.97.1 and all 6 targets (`rust-toolchain.toml`), crate versions (`Cargo.lock`), NDK
    version (`androidNdkVersion`).
  - Pinned only for the Docker agent: `cargo-ndk` 4.1.2 (`.claude/skills/docker-agent/docker/Dockerfile`).
  - Not pinned on developer hosts: the `cargo-ndk` version, and Xcode/clang for the Apple targets.

### Measured baseline [Linux]

- `./gradlew --no-daemon :ethwallet-kotlin:buildRustAndroid`: succeeded in 6m20s. This was one cold, no-daemon run
  in a Linux VM.
- `./gradlew :ethwallet-kotlin:prepareKotlinIdeaImport` (the task that IDE sync runs): failed after 22s.
  - The task graph includes `buildRustIosArm64`, `buildRustIosSimulatorArm64`, and `buildRustIosX64`. The cause is
    `dependsOn(rustBuildTask)` on the cinterop tasks.
  - The failure: `secp256k1-sys` cannot run `clang` for `aarch64-apple-ios`.
  - Result: IDE sync of `:ethwallet-kotlin` fails on Linux.
- The same run with the `dependsOn(rustBuildTask)` lines removed from the cinterop tasks succeeded on Linux.
  - This was a throwaway worktree, not part of this change.
  - It took 3m14s with `--no-daemon`, including the Kotlin/Native distribution download. This is not a sync time.
  - On Linux the iOS cinterop tasks are skipped, so this does not prove the fix works on macOS.
- `./gradlew :ethwallet-kotlin:jvmProcessResources`: fails on Linux, because it cross-compiles
  `aarch64-apple-darwin` with the host `cc`. This is expected for an Apple Silicon-only desktop target.

### Baseline on macOS [Inference, Unverified]

The cinterop block passes `-libraryPath` and `-staticLibrary libethwallet_crypto.a`. Because of this, cinterop
consumes the archive, and IDE sync compiles Rust for all three iOS targets on every cold sync.

## Option 1: prebuilt or published artifacts

Build Rust outside Gradle (a script or CI). Publish the `.so`, `.dylib`, and `.a` files (for example as a Maven
artifact or an XCFramework). Gradle only downloads them.

- **Coverage**: all three platforms, if a builder exists for each. Apple binaries need a macOS host.
- **Workflow**:
  - Every ABI change needs this sequence: build, publish, bump the version, then change the Kotlin side.
  - CLAUDE.md requires the Rust function, C header, JNI bridge, cinterop call, and tests to stay synchronized.
    Published artifacts add a version skew window between them.
  - The `EWC_ABI_VERSION` check detects skew only at runtime.
- **IDE sync**: fast. There is no cargo in the sync graph.
- **Cost**:
  - It needs a publishing pipeline and a repository. Neither exists, and there is no CI.
  - Only the owner's Mac can build the Apple artifacts.
  - For one consumer in the same repository, this adds work and saves none.
- **Reproducibility**: good for consumers, because they all get the same bytes. It moves the problem to the
  builder, which is today an unpinned local Xcode.
- **Security** [Inference]:
  - A tampered or wrongly built release artifact would sign transactions and hold keys.
  - Source builds let a reviewer see the code that runs. Artifacts would need checksums or signatures, and a
    trusted builder.
- **Interop**: no change.
- **Verdict**: rejected for now. Revisit when CI with macOS runners exists. It can then be a cache in front of the
  source build, not a replacement.

## Option 2: an established Rust Gradle plugin

Two candidates were tested. Mozilla's plugin is used by Mozilla's Android application-services. Gobley is the
actively developed fork of the UniFFI Kotlin Multiplatform plugins. Ubique's `ch.ubique.uniffi.plugin`, another
fork, was not tested. The plugin trials reached configuration only, not a full build.

### rust-android-gradle (`org.mozilla.rust-android-gradle.rust-android`)

- [Source] https://github.com/mozilla/rust-android-gradle: v0.11.0 released 2026-11-16. Requires AGP 9 and Gradle
  9.1. Android only. Its desktop targets are for host unit tests.
- [Linux] Version 0.11.0 was applied to `:ethwallet-kotlin` in a throwaway worktree.
  - Configuration succeeds.
  - No `cargo*` task is registered with `com.android.kotlin.multiplatform.library`, so the plugin does nothing.
- It has no iOS support and no JVM resource packaging.

### Gobley (`dev.gobley.cargo`, `dev.gobley.uniffi`, `dev.gobley.rust`)

- [Source] https://github.com/gobley/gobley:
  - Last release v0.3.7 on 2025-10-08. The repository was last pushed on 2026-10-02.
  - The Cargo plugin can be used without UniFFI. It supports Android, JVM, and Kotlin/Native.
- [Source] AGP 9 and the KMP Android library plugin are not supported in a release. These Gobley GitHub issues and
  PRs (not Agent Board tasks) are open:
  - issue gobley/gobley#153
  - issue gobley/gobley#288
  - PR gobley/gobley#280 (not merged)
  - PR gobley/gobley#282 (not merged)
- [Linux] Version 0.3.7 was applied to `:ethwallet-kotlin` in a throwaway worktree. Configuration failed:
  `Android JVM targets are added, but Android Gradle Plugin is not found.`
- Its JVM layout follows JNA resource prefixes. The current JNI loader would have to change.

### Verdict

Rejected. Neither tested plugin works with AGP 9.3.2 and `com.android.kotlin.multiplatform.library` today.
Re-check Gobley if a release adds that support.

## Option 3: typed convention plugin

Move the cargo tasks, target table, packaging, and host-library wiring into an included build (`build-logic`). Use
typed tasks with `@Input`/`@OutputFile` properties and an extension (for example
`ethwalletRust { library = "ethwallet_crypto" }`).

- **Coverage**: same as today. No platform is lost.
- **Workflow**: source build stays. A change to the Rust ABI and to Kotlin stays in one commit.
- **IDE sync**: the fix is in the wiring, not in the plugin. It has two steps.
  - Step A, Linux sync: add the iOS cargo dependency only when the host is macOS. On other hosts the iOS cinterop
    tasks are skipped, so they do not need the archive.
    - [Linux] Sync passes on Linux when the cinterop tasks do not depend on cargo. This proves only that the Linux
      failure goes away. It does not test the macOS path.
  - Step B, macOS sync (optional): move `-libraryPath`/`-staticLibrary` from cinterop `extraOpts` to the binaries'
    `linkerOpts`, and remove the cinterop dependency on cargo. Then sync needs only the header.
    - Cost [Inference]: cinterop `-staticLibrary` packs the archive into the klib, and downstream iOS frameworks get
      it automatically. `linkerOpts` applies only to the binaries of `:ethwallet-kotlin` (today, test executables).
      A future iOS framework in another module must then link `libethwallet_crypto.a` itself.
    - This is acceptable while no module consumes iOS. iOS opt-in (option 5 variant) is the alternative.
    - [Unverified] On macOS the cinterop and link tasks work after the move.
- **Cost**:
  - One new included build. The cost is small, because the build file already has `cargoInputs` and a typed
    `RustNativeLibrarySync`.
  - It removes the three copies of the host-library wiring and the hard-coded cross-project path. `shared` and
    `app` would get the path from the plugin or from a consumable configuration.
  - REVIEW.md "no abstraction just for abstraction": this is justified by three copies of the logic, not one.
  - `buildSecureEnclaveMacosArm64` is Swift. It stays in `shared`, because it has one user.
- **Reproducibility**: same as today. Typed inputs make it practical to add `cargo-ndk` and NDK version checks, and
  to check configuration-cache compatibility.
- **Interop**: no change.
- **Verdict**: recommended.

## Option 4: UniFFI-generated bindings

#277 Try UniFFI for Rust/KMP bindings builds the prototype. This section only lists what the prototype must
answer.

- **Coverage**:
  - Upstream UniFFI (v0.32.2) generates Kotlin for JVM and Android only.
  - Kotlin/Native (iOS) needs Gobley. Gobley pins `uniffi = "=0.29.5"`
    ([Source] https://github.com/gobley/gobley/blob/main/Cargo.toml) and is blocked by AGP 9 (see option 2).
- **Workflow**: source build. The Gradle integration problem stays. UniFFI removes the binding code, not the build
  wiring.
- **IDE sync**: same as the build integration chosen.
- **Cost**:
  - Removes about 6 JNI exports, the C header, the `.def` file, and three handwritten `NativeCrypto` actuals.
  - Adds the `uniffi` crates, generated code, and JNA on Android. JNA is already a desktop dependency of `shared`.
  - The ABI surface is small, so the saving is small.
- **Interop** [Inference, to be verified by #277 Try UniFFI for Rust/KMP bindings]:
  - Generated bindings pass byte arrays through `RustBuffer` copies that the app does not own. This conflicts
    with these CLAUDE.md rules:
    - Kotlin owns output buffers and clears them.
    - Rust writes output only on success.
    - Secret copies are `Zeroizing`.
  - These rules would need a rewrite.
- **Reproducibility**: same as the build integration chosen. The bindgen version must match the `uniffi` crate.
- **Verdict**: deferred to #277 Try UniFFI for Rust/KMP bindings. Adopt only if its prototype shows that secret
  handling stays at the current level and iOS works with AGP 9.

## Option 5: temporarily narrow to Android only

- **Coverage**: drops macOS JVM and iOS.
- Android only is not possible without losing shipped features:
  - `desktopApp` uses the macOS JVM library.
  - `shared` and `ethwallet-kotlin` Android host tests load the macOS dylib.
  - The Sepolia E2E test loads the macOS dylib.
- **Variant: iOS opt-in.** Declare the three iOS targets only when a Gradle property such as `-PethwalletIos=true`
  is set.
  - Nothing consumes iOS today.
  - This removes three cargo builds from the sync graph when the property is not set.
  - `rust-toolchain.toml` still lists all six targets, so rustup still installs the iOS targets. Removing them
    from the file would make iOS builds need a manual `rustup target add`.
  - Cost: about 10 lines of Gradle.
  - Risk: iOS code (`NativeCrypto.ios.kt`) is no longer compiled by default and can break without notice.
- **IDE sync**: Android-only or iOS opt-in removes the iOS cargo builds from sync.
- **Reproducibility and interop**: no change for the remaining targets.
- **Verdict**: Android only is rejected. iOS opt-in is a valid, cheap alternative to the sync fix in option 3, but
  it hides iOS breakage. This is a decision for the owner.

## Comparison

- **Android**
  - Baseline: yes.
  - Option 1, prebuilt: yes.
  - Option 2, plugin: rust-android-gradle no-op; Gobley fails.
  - Option 3, convention plugin: yes.
  - Option 4, UniFFI: yes, with JNA.
  - Option 5, Android only: yes.
- **macOS JVM**
  - Baseline: yes.
  - Option 1, prebuilt: yes, with a Mac builder.
  - Option 2, plugin: Gobley only, which fails.
  - Option 3, convention plugin: yes.
  - Option 4, UniFFI: yes, with JNA.
  - Option 5, Android only: dropped.
- **iOS**
  - Baseline: yes, with no consumer.
  - Option 1, prebuilt: yes, with a Mac builder.
  - Option 2, plugin: Gobley only, which fails.
  - Option 3, convention plugin: yes.
  - Option 4, UniFFI: needs Gobley, which is blocked.
  - Option 5, Android only: dropped. The variant makes it opt-in.
- **Workflow**
  - Baseline: source build.
  - Option 1, prebuilt: publish, then bump.
  - Option 2, plugin: source build.
  - Option 3, convention plugin: source build.
  - Option 4, UniFFI: source build and bindgen.
  - Option 5, Android only: source build.
- **IDE sync**
  - Baseline: fails on Linux. On macOS, cold sync builds three iOS targets (unverified).
  - Option 1, prebuilt: fast.
  - Option 2, plugin: not applicable.
  - Option 3, convention plugin: Linux fixed by step A (failure gone on Linux). macOS needs step B, which is
    unverified and moves the archive out of the klib.
  - Option 4, UniFFI: same as the chosen build.
  - Option 5, Android only: fixed.
- **Cost**
  - Baseline: three copies of the wiring.
  - Option 1, prebuilt: new pipeline, with no CI.
  - Option 2, plugin: blocked.
  - Option 3, convention plugin: small.
  - Option 4, UniFFI: new dependencies and rule rewrite.
  - Option 5, Android only: features lost. The variant costs about 10 lines.
- **Reproducibility**
  - Baseline: toolchain, lock, and NDK pinned.
  - Option 1, prebuilt: depends on the builder.
  - Option 2, plugin: not applicable.
  - Option 3, convention plugin: same as baseline, with room to add checks.
  - Option 4, UniFFI: same as the chosen build.
  - Option 5, Android only: same as baseline.
- **Interop**
  - Baseline: handwritten JNI and C.
  - Option 1, prebuilt: no change.
  - Option 2, plugin: JNA layout with Gobley.
  - Option 3, convention plugin: no change.
  - Option 4, UniFFI: generated code; secret-handling rules at risk.
  - Option 5, Android only: JNI only.

## Proposed follow-ups

These are not created on the board. The owner decides.

1. Fix IDE sync. Step A: depend on the iOS cargo tasks only on macOS hosts. Step B, the owner's choice: move the
   static library from cinterop to the link options, and remove the cinterop dependency on cargo.
2. Add `build-logic` with a typed Rust convention plugin. Replace the host-library wiring in `shared` and `app`.
3. Optional, the owner's choice: make the iOS targets opt-in with a Gradle property.
4. After #277 Try UniFFI for Rust/KMP bindings: decide on UniFFI from its results.

## Manual checks (macOS, Apple Silicon)

Run from the repository root on a clean checkout.

1. IDE sync baseline, cold:
   - Run `./gradlew --stop && rm -rf ethwallet-kotlin/build/rust`.
   - Then run `time ./gradlew :ethwallet-kotlin:prepareKotlinIdeaImport --console=plain`.
   - Expected: the three `buildRustIos*` tasks run. Record the time.
2. IDE sync baseline, warm: run the same command again. Expected: the cargo tasks are `UP-TO-DATE`. Record the time.
3. Sync fix check (step B):
   - Apply step B of follow-up 1 locally: move `-libraryPath`/`-staticLibrary` to
     `binaries.all { linkerOpts(...) }`, and remove the cinterop `dependsOn`.
   - Run:

     ```bash
     ./gradlew :ethwallet-kotlin:prepareKotlinIdeaImport \
       :ethwallet-kotlin:linkDebugTestIosSimulatorArm64 \
       :ethwallet-kotlin:iosSimulatorArm64Test
     ```

   - Expected: sync runs no cargo task. The link and the tests pass.
   - No module builds an iOS framework today, so this check cannot test a downstream consumer. Record that the
     archive is no longer in the klib. Find it with
     `find ethwallet-kotlin/build -name '*cinterop*ethwallet_crypto*.klib'`. Then `unzip -l` on it should list no
     `libethwallet_crypto.a`. Before step B, it should list one. If the klib is a directory, use
     `find ethwallet-kotlin/build -path '*cinterop*' -name 'libethwallet_crypto.a'` instead.
4. macOS JVM: run `./gradlew :ethwallet-kotlin:jvmTest`. Expected: pass.
5. Android host tests with the dylib: run
   `./gradlew :ethwallet-kotlin:testAndroidHostTest :shared:testAndroidHostTest`. Expected: pass.
6. Desktop app: run `./gradlew :desktopApp:run`. Expected: the app starts and can create a wallet.
