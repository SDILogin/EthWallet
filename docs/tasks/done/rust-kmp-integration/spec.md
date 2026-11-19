# Task

#28 Research alternatives for Rust/KMP integration

Evaluate alternatives to the current manual source-build Gradle integration of `:ethwallet-kotlin`. Recommend a
path for EthWallet.

## Scope

Compare these options with the current setup:

1. Build Rust outside Gradle and consume published or prebuilt artifacts.
2. Use an established Rust Gradle plugin.
3. Move the existing target, build, and packaging logic into a typed Gradle convention plugin.
4. Adopt UniFFI-generated Kotlin bindings.
5. Temporarily narrow support to Android only.

Compare each option by:

- Android, macOS JVM, and iOS coverage.
- Source-build versus artifact-publishing workflow.
- IDE sync behavior.
- Dependency and maintenance cost.
- Reproducibility.
- JNI and C-interop implications.

#277 Try UniFFI for Rust/KMP bindings builds a working prototype for option 4. This task only compares and
recommends.

## Out of scope

- Changes to build files or source code.
- A UniFFI prototype.
- New Agent Board tasks. The research lists proposed follow-ups only.

## DOD

- docs/tasks/done/rust-kmp-integration/research.md contains the comparison and the recommendation.
- Each claim is tagged by its evidence: ran on Linux, primary source, or unverified on macOS.
- Checks that need macOS or iOS are listed with exact manual steps.
