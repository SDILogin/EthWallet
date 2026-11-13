---
name: unit-tests
description: Generate unit tests for Kotlin/Android files. Use when user wants to create tests, write tests, or says "generate tests" for a specific file. Follows project patterns with fakes over mocks, linear test flows, and meaningful assert messages.
---

## Core Rules

### No Conditional Asserts

Never wrap asserts in conditional flows. Tests must be linear without branching.

```kotlin
// WRONG - conditional assert
result?.let {
    assertEquals(expected, it.value, "message")
}

// WRONG - if guard
if (state.hasError) {
    assertNotNull(state.error, "message")
}

// CORRECT - direct assert
assertNotNull(result, "Result should not be null")
assertEquals(expected, result.value, "Value should match expected")
```

Use List extension functions instead of loops:
```kotlin
// WRONG
for (item in items) {
    assertTrue(item.isValid, "message")
}

// CORRECT
assertTrue(items.all { it.isValid }, "All items should be valid")
```

### Prefer Fakes Over Mocks

Mock frameworks hardcode method calls, breaking tests during refactoring.

```kotlin
// WRONG - MockK ties to implementation
coEvery { repository.getUser(any()) } returns user

// CORRECT - fake allows refactoring
class FakeUserRepository : UserRepository {
    var userToReturn: User? = null
    override suspend fun getUser(id: String) = userToReturn
}
```

### Assert Messages

Explain what's broken from user's perspective:

```kotlin
// WRONG - describes code
assertEquals(true, state.isEnabled, "isEnabled should be true")

// CORRECT - describes user impact
assertEquals(true, state.isEnabled, "Send button should be enabled after valid input")
```

### Test Scope

Don't test every edge case. Tests should survive non-global refactoring. Focus on:
- User-facing behavior
- Critical business logic
- Integration points

## Coroutines Testing

Tests in `shared/src/commonTest` use `kotlin.test` annotations and assertions.

### setUp sets the main dispatcher
```kotlin
private val testDispatcher = StandardTestDispatcher()

@BeforeTest
fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeRepository()
    viewModel = MyViewModel(repository)
}

@AfterTest
fun tearDown() {
    Dispatchers.resetMain()
}
```

### @Test uses runTest
```kotlin
@Test
fun input_change_updates_the_state() = runTest(testDispatcher) {
    viewModel.onEvent(MyEvent.InputChanged("value"))
    advanceUntilIdle()

    assertEquals(expected, viewModel.viewState.value, "State should update after input")
}
```

### Flow Collection Pattern

Prevents hanging on infinite flows:
```kotlin
private val collectedStates = mutableListOf<MyState>()

private fun TestScope.collectStates() {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.viewState.collect { state ->
            collectedStates.add(state)
        }
    }
}
```

## ViewModel Testing

### Main dispatcher

Tests in `shared/src/commonTest` set the main dispatcher in `setUp` (see above). `MainDispatcherRule` is a JUnit rule in
`app/src/test`. Use it only for tests in that source set.

### Initial loading starts on the first collector

A ViewModel that loads initial data does so inside `viewState.onStart {}`. For such a ViewModel, collect `viewState` before the assertions:
```kotlin
@Test
fun invalid_input_shows_an_error() = runTest(testDispatcher) {
    collectStates()
    viewModel.onEvent(MyEvent.InputChanged("invalid"))
    advanceUntilIdle()

    assertNotNull(viewModel.viewState.value.error, "Error should appear for invalid input")
}
```

## Logger

Always enable logger for tracing test failures:
```kotlin
import me.sdi.ethwallet.Logger

@BeforeTest
fun setUp() {
    Logger.enabled = true
    // ...
}
```

## Helper Functions

Use helpers to clarify test intent:
```kotlin
private fun TestScope.inputPin(pin: String) {
    pin.forEach { digit ->
        viewModel.onEvent(PinEvent.OnDigitClick(digit.toString().toInt()))
    }
    advanceTimeBy(400)
}

private fun TestScope.submitForm() {
    viewModel.onEvent(FormEvent.Submit)
    advanceUntilIdle()
}
```

## Test Structure

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: MyViewModel
    private val collectedNews = mutableListOf<MyNews>()

    @BeforeTest
    fun setUp() {
        Logger.enabled = true
        Dispatchers.setMain(testDispatcher)
        repository = FakeRepository()
        viewModel = MyViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_is_empty_before_the_first_collector() = runTest(testDispatcher) {
        val state = viewModel.viewState.value

        assertFalse(state.isLoading, "Should not be loading initially")
        assertTrue(state.items.isEmpty(), "Items should be empty initially")
    }

    @Test
    fun items_load_when_the_screen_collects_the_state() = runTest(testDispatcher) {
        repository.itemsToReturn = listOf(Item("1"), Item("2"))

        collectStates()
        advanceUntilIdle()

        val items = viewModel.viewState.value.items
        assertEquals(2, items.size, "Should load 2 items from repository")
    }
}
```