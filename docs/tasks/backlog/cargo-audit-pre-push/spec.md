# Task

#26 Run cargo audit in the pre-push hook

## Background

The `ethwallet-kotlin` Rust crate in `ethwallet-kotlin/rust` has third-party dependencies. Nothing checks them for
known RustSec vulnerabilities.

## Scope

- `tools/pre-push` runs `cargo audit` on `ethwallet-kotlin/rust/Cargo.lock`. `tools/setup.sh` installs the hook.
- A push fails when a Rust dependency has a known RustSec vulnerability.
- If cargo-audit is not installed, the hook prints the install command and fails.
- `CLAUDE.md` documents the check and the install command next to the other Rust commands.

Out of scope: CI, cargo deny, license rules, and build matrices.

## Accepted risks

- Each push fetches the RustSec advisory database. A push needs network access.
- A new advisory blocks all pushes until it is fixed.

## Definition of done

- The pre-push hook fails when `cargo audit` fails.
- The pre-push hook fails with the install command when cargo-audit is not installed.
- `CLAUDE.md` lists `cargo audit` and `cargo install cargo-audit --locked`.
