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

Found in PR #27 (#10 Find long-running main thread operations; set up profiler and StrictMode). Static review only. Nothing was measured.

## Scope

- Create one date formatter for each time zone and reuse it for all rows.
- Calculate the today and yesterday keys one time for each time zone, not on each recomposition.
- Keep the same group keys and the same displayed text.

## Definition of done

- No formatter is created for each row or each recomposition.
- `./gradlew spotlessApply` and the shared tests pass.
