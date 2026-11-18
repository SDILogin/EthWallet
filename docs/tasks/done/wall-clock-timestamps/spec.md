# Task

Store real wall-clock times for cached fiat rates and token balances.

## Background

`FiatRateProviderCoinbase` (`lastUpdatedTimestamp`) and `EthBalanceProvider` (`TokenBalanceEntity.timestamp`) take a
`now: () -> Long` constructor lambda. Its default is
`TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds`. That expression measures the time since the mark
it has just made, so it always returns about 0. In production every stored timestamp is about 0. Tests do not see this,
because they pass their own `now`.

Today no production code reads these timestamps. The values are wrong data, not a visible bug yet. Any future
staleness check would be wrong.

Found in PR #22 (Add transaction result screen) review.

## Scope

- Change the default `now` in both classes to a real clock: `{ Clock.System.now().toEpochMilliseconds() }`. Follow the
  REVIEW.md rule for the `now` lambda.
- Add a test for each class that builds it without `now` and checks that the stored timestamp is after 2025-01-01. The
  test does not read the clock, so it is deterministic.
- No DB migration: the column type stays `Long` in epoch milliseconds.

## Definition of done

- New rows of both tables store epoch milliseconds of the write time.
- `./gradlew spotlessApply` and the shared tests pass.
