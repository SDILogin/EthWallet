# Review Rules

Rules that code review checks. Each rule comes from earlier review feedback.

## Security

- Do not use the PIN as encryption key material. The PIN is an app-lock check. Anyone with access to
  the app process can brute-force a PIN-derived key offline, where the attempts guard never runs.
  Protect secrets with the platform key store (AndroidKeyStore) or owner-only storage.
- Do not add security layers that only look protective. If a layer does not stop a realistic
  attacker, remove it and keep the flow simple.

## Architecture

- A `Storage<Data>` is responsible only for persisting `Data`. Business logic such as sessions, auto-lock,
  and attempt counters belongs in dedicated classes such as interactors.

## Functions

- Do not give private functions default parameter values; keep calls explicit. Default parameters are
  allowed only in design-system components.
- Prefer passing data (for example, a map of values to write) over callbacks. Callbacks make the
  execution flow harder to read.

## Preconditions

- When the navigation flow guarantees a precondition, assert it with `checkNotNull` / `check`.
  Do not return a fallback value such as `false` for a state that cannot happen.

## ViewModels

- Trace `onEvent` handlers and remove dead branches. Consider only realistic user scenarios: `OnStart`
  after pull-to-refresh or a click on a disabled button are not realistic.
- Do not hardcode user-facing strings in a ViewModel. Expose an `enum` (or sealed type) and map it
  to a string resource in the Compose layer, which can localize the text.
- Send one-shot UI effects (shake animation, toast, navigation) through `news`. Do not trigger them
  by putting counters or IDs in the view state.
- Declare constants as file-level `private const val`, not file-level `private val`. `Duration` is
  not allowed in a `const`, so give time constants a unit suffix and a primitive value, for example
  `private const val INDICATOR_ANIMATION_DELAY_MS = 300`, and convert to `Duration` where the value
  is used (`delay(INDICATOR_ANIMATION_DELAY_MS.milliseconds)`).

## Compose UI

- Collect `StateFlow` in composables with `collectAsStateWithLifecycle()`, not `collectAsState()`.
- Do not change the layout when state changes. A conditional element (an error or lock message, for
  example) must always be in the UI tree. Control its visibility with alpha (`graphicsLayer` /
  animated alpha) so nearby elements such as the PIN indicator do not jump.


## Code style

- EthAddress value class over String. String is allowed only for data passing outside the app (serialization to bundle,
  db).
- ViewModel can expose viewState as a StateFlow, news as a Flow (backed by channel), onEvent method with a single event
  sealed interface param.
- Repositories must follow CRUD style when possible. Allowed methods: create, read, update, delete, observe. Any
  additional methods must be also relevant to collection interface (`find(id)` for example). Bad method:
  `getPendingTransactions` -- not relevant to collection in general.
- `try..catch` must respect CancellationException. Non-suspendable functions should not handle CancellationException.
  Flow methods that works with exception already handles CancellationException under the hood. Inside
  `catch(exception) { }` is valid to assume that exception is not a CancellationException.
- suspend function must be safe to call from main thread. Dispatcher must be set on the lowest level. For example, file
  access function (io operation) must set `Dispatchers.IO` right where disk access happens. Suspendable function should
  not rely on client code -- client code is not responsible for dispatcher selection by default.
- Error handling must be on the view model level. Interactor must rethrow exception instead of trying to wrap exception.
  Interactor and repositories must be flat. ViewModel must assume that all suspendable functions of interactor and
  repository can potentially throw exceptions. Exception -- public function that return Flow. Flow creation must be
  safe, exception propagation happens inside flow.
- No abstraction just for abstraction. If abstraction layer does nothing except calling a dependency, delete it to
  simplify code.
- Interface with a single implementation is allowed in 2 cases: `Fake` or `InMemory` implementation in tests over the
  real implementation; isolate android specific code from pure kotlin code. In any other scenarios interface must be
  deleted.
- Repositories must be replaced with `InMemory` implementation in tests. Forbidden to mock repositories.
- `InMemory` repositories implementation must be simple abstraction over collections without any complex logic. That's
  why repositories must be simple CRUDs.

## Presentation logic

- Assume that Start event arrives first. It is forbidden to handle unrealistic events flow like pull-to-refresh before
  OnStart.
- Concurrency primitives like Lock, synchronized blocks and so on must be treated as overengineering. Agent must provide
  A REALISTIC evidence that concurrent primitive is mandatory.
- ViewModel `init` must be fast and error-safe. Actual initialization (for example initial data loading) must be
  triggered from inside `viewState.onStart {}`.

## Jetpack compose style

- By default, all screens (panes) must have a single public composable function `*Screen` or `*Pane` with minimal params
  list. Minimal params list is modifier, and navigation callbacks (`onBackPress` or `onTransactionClick(transaction)`
  for example).
- ViewModel binding to composable function must be done inside the single public composable function. Also, in the
  public screen function must be observed a `viewState` changes.
- Actual layout must be in `*ScreenContent` or `*PaneContent`. For content must be generated a `@FormFactorPreviews`.
  Use `PreviewParameterProvider` if preview must be provided for multiple states.
- `SubcomposeLayout` (or `BoxWithConstraints`) is a red flag. Always push back on it. `BoxWithConstratints` can be used
  only for adaptive layout creation and never to measure a UI element size (for size info retrieval use dedicated
  modifiers).

## Business logic

- User can have multiple wallets in the app.
- Self transfer is a transfer from one of user wallets to another one. Not only from `wallet1` to `wallet1`. Transfer
  from `wallet1` to `wallet1` high probably never happens.  
