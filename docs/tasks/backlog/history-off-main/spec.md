# Task

#327 Map and decode transfer history off the main thread

## Background

The History screen does work on the main thread that grows with each loaded page.

- `shared/src/commonMain/kotlin/me/sdi/ethwallet/core/history/TransferHistoryRepository.kt:34`: `observe` maps all
  rows with `.map` and has no `flowOn`. Room runs the query off Main, but the `map` lambda runs in the collector
  context, which is `viewModelScope` (Main).
- `shared/src/commonMain/kotlin/me/sdi/ethwallet/ui/history/HistoryViewModel.kt:75`: `combine` maps each row to
  `HistoryRow` and formats amounts with `BigDecimal`. It runs again for the whole list on each DB change, also on
  each upsert during refresh.
- `HistoryViewModel` calls `HistoryUpdateInteractor.refresh` and `loadMore` from `viewModelScope`. No interactor,
  repository or provider function sets a dispatcher:
  - `HistoryUpdateInteractor.kt:29` reads and maps the full cached table.
  - `TransfersHistoryProviderBlockscout.kt:126` decodes each page with `body()`.
  - `TransfersHistoryProviderBlockscout.kt:62` decodes one more transaction for each outgoing token transfer.
  - Ktor 3.6.0 `ContentNegotiation` and `KotlinxSerializationConverter` do not change the dispatcher. So JSON
    decoding runs on the caller dispatcher.

This breaks the `REVIEW.md` rule "suspend function must be safe to call from main thread".

Estimated severity: medium. Platforms: Android and Desktop.

Found in PR #27 (#10 Find long-running main thread operations; set up profiler and StrictMode). Static review only. Nothing was measured.

## Scope

- Run the row mapping in `TransferHistoryRepositoryRoom.observe` and the `HistoryRow` mapping in `HistoryViewModel`
  on `Dispatchers.Default`.
- Decode and map Blockscout responses on `Dispatchers.Default` at the lowest level, in
  `TransfersHistoryProviderBlockscout`.
- Map the cached table in `HistoryUpdateInteractor` on `Dispatchers.Default`.

## Definition of done

- History mapping and JSON decoding do not run on the main thread.
- `./gradlew spotlessApply` and the shared tests pass.
