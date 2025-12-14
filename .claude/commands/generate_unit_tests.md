Generate comprehensive unit tests for the file at $ARGUMENTS.

Testing requirements:
- Write tests only. Do not modify or touch any other code.
- NEVER add code comments.
- ALWAYS add error message to asserts. The message must describe the problem when the test fails.
- Do not use runTest inside setUp method. Prefer runBlocking.
- Add helper functions to clarify tests.
- Use `@get:Rule val mainDispatcherRule = MainDispatcherRule()` when testing ViewModels.
- Use `me.sdi.ethwallet.Logger` to trace execution flow.

Assert example:
```kotlin
assertNotNull("Error message should be present after wrong PIN", stateWithError.errorMessage)
```

Helper function examples:
```kotlin
private fun TestScope.inputPin(pin: String) {
    pin.forEach { digit ->
        viewModel.onEvent(VerifyPinCodeEvent.OnDigitClick(digit.toString().toInt()))
    }
    advanceTimeBy(400)
}

private fun TestScope.collectNews() {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.news.collect { news ->
            collectedNews.add(news)
        }
    }
}
```

Please create thorough tests based on the existing codebase patterns, covering all major functionality in the file.