---
name: unit-tests
description: Generate unit tests for Kotlin/Android files. Use when user wants to create tests, write tests, or says "generate tests" for a specific file. Follows project patterns with fakes over mocks, linear test flows, and meaningful assert messages.
---

# Unit Tests

Generate comprehensive unit tests following project patterns.

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
// WRONG - mockito ties to implementation
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

### setUp uses runBlocking
```kotlin
@Before
fun setUp() = runBlocking {
    repository = FakeRepository()
    viewModel = MyViewModel(repository)
}
```

### @Test uses runTest
```kotlin
@Test
fun `should update state after event`() = runTest {
    viewModel.onEvent(MyEvent.Start)
    advanceUntilIdle()

    assertEquals(expected, viewModel.state.value, "State should update after Start")
}
```

### Flow Collection Pattern

Prevents hanging on infinite flows:
```kotlin
private val collectedStates = mutableListOf<MyState>()

private fun TestScope.collectStates() {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.state.collect { state ->
            collectedStates.add(state)
        }
    }
}
```

## ViewModel Testing

### MainDispatcherRule Required
```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

### Send Start Event First

If ViewModel accepts initialization event, always send it:
```kotlin
@Test
fun `should show error on invalid input`() = runTest {
    viewModel.onEvent(MyEvent.Start)  // Always first
    viewModel.onEvent(MyEvent.InputChanged("invalid"))

    assertNotNull(viewModel.state.value.error, "Error should appear for invalid input")
}
```

## Logger

Always enable logger for tracing test failures:
```kotlin
import me.sdi.ethwallet.Logger

@Before
fun setUp() = runBlocking {
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
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: MyViewModel
    private val collectedNews = mutableListOf<MyNews>()

    @Before
    fun setUp() = runBlocking {
        Logger.enabled = true
        repository = FakeRepository()
        viewModel = MyViewModel(repository)
    }

    @Test
    fun `should initialize with default state`() = runTest {
        val state = viewModel.state.value

        assertFalse(state.isLoading, "Should not be loading initially")
        assertTrue(state.items.isEmpty(), "Items should be empty initially")
    }

    @Test
    fun `should load items on start`() = runTest {
        repository.itemsToReturn = listOf(Item("1"), Item("2"))

        viewModel.onEvent(MyEvent.Start)
        advanceUntilIdle()

        val items = viewModel.state.value.items
        assertEquals(2, items.size, "Should load 2 items from repository")
    }
}
```