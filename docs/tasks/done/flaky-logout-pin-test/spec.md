# Task

#325 Fix flaky WalletFlowTest lock screen PIN after logout test

## Background

`WalletFlowTest.lock_screen_still_accepts_the_pin_after_a_logout_and_a_new_wallet` failed one time in a full
`./gradlew :app:testDebugUnitTest` run on Linux (Robolectric). The error was `java.lang.AssertionError` at
`WalletFlowTest.kt:576`. The test passed when it ran alone, and it passed in a full rerun.

Line 576 is `composeTestRule.assertTextExists("Create PIN Code")`. It runs directly after the click on
"Confirm logout". The logout is asynchronous. The similar test above it uses `waitUntilTextExists("Create PIN Code")`.
This is the probable cause. It is not confirmed.

## Scope

- Find the cause of the failure.
- Make the test wait for the state that it asserts. Do not add sleeps or retries.
- Check the other `assertTextExists` calls in `WalletFlowTest` that follow an asynchronous action.

## Result

- Cause: "Confirm logout" runs `dataCleaner.clean()`. `DataCleanerRoomDb` clears the tables on `Dispatchers.IO`.
  Compose test idling does not wait for `Dispatchers.IO`. The `LoggedOut` navigation can come after the assertion.
- The cause is confirmed by reproduction with an injected delay. A temporary cleaner with a 300 ms delay on
  `Dispatchers.IO` made line 576 fail. With `waitUntilTextExists`, the test passed with the same delay.
- Line 576 now uses `waitUntilTextExists("Create PIN Code")`.
- The other `assertTextExists` calls have no step on an untracked dispatcher between the action and the text. They
  are not changed.

## Definition of done

- The test passes in repeated full `:app:testDebugUnitTest` runs.
- `./gradlew spotlessApply` passes.
