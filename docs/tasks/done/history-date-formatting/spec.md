# Task

#328 Reuse date formatters in the History screen

## Background

- `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/history/HistoryScreen.kt:277`: `remember(rows, timeZone.id)`
  calls `historyDateKey` for each row. `historyDateKey` (`HistoryPresentation.kt:38`) creates a new
  `SimpleDateFormat` for each call.
- `HistoryScreen.kt:286-289`: `Calendar.getInstance` and two `historyDateKey` calls on each recomposition, outside
  `remember`.
- `HistoryScreen.kt:444`: `formatHistoryTime` creates a `DateFormat` for each visible item.

This work runs in composition on the main thread. It grows with each loaded page.

Estimated severity: medium. Platforms: Android and Desktop.

Found in GitHub PR #27 for #10 Find long-running main thread operations; set up profiler and StrictMode.
Static review only. Nothing was measured. Severity: high is more than 16 ms on a frame. Medium grows with data size
or runs often. Low is a few ms or less, or one time at startup.

## Scope

- Create one date formatter for each time zone and reuse it for all rows.
- Group rows by the local day number. Do not format a date string for a group key.
- Remove the Today and Yesterday headers. Each day header shows the date in the device locale format. The PR #35 review
  asked for this.
- `TransactionDetailsScreen.kt` uses the same `HistoryDateFormats` class, because the old top-level format functions
  are removed.
- Keep the same displayed text for times, other date headers and transaction details.

## Definition of done

- No formatter is created for each row or each recomposition.
- `./gradlew spotlessApply` and the shared tests pass.
