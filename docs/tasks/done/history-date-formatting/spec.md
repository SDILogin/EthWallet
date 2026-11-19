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
- Calculate the today and yesterday keys one time for each time zone, rows list and refresh, not on each
  recomposition. A refresh after midnight updates the Today and Yesterday headers.
- `TransactionDetailsScreen.kt` uses the same `HistoryDateFormats` class, because the old top-level format functions
  are removed.
- Keep the same group keys and the same displayed text.

## Definition of done

- No formatter is created for each row or each recomposition.
- `./gradlew spotlessApply` and the shared tests pass.
