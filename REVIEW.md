# Review rules

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
- A `Storage<Data>` is responsible only for persisting `Data`. Business logic such as sessions, auto-lock, and attempt
  counters belongs in dedicated classes such as interactors.
- Use named arguments when pass multiple primitive values. For example,
  `private class PinArtifacts(val saltBase64: String, val wrappedBase64: String)` must be constructed using named
  arguments.
- Do not give private functions default parameter values; keep calls explicit. Default parameters are allowed only in
  design-system components.
- Prefer passing data (for example, a map of values to write) over callbacks. Callbacks make the execution flow harder
  to read.
- Prefer positive conditions: `if (predicate()) { ... } else { ... }` over `if (!predicate())` with an early return.
- Use `checkNotNull` (or `check`) for a state that the app flow guarantees. Do not return a fallback value for an
  impossible state. For example, the PIN verification screen is shown only after a PIN storage check, so the stored PIN
  artifacts are not null there.
- Declare constants as file-level `private const val`, not file-level `private val`. `Duration` cannot be a `const`, so
  store time constants as primitives with a unit suffix: `private const val INDICATOR_ANIMATION_DELAY_MS = 300`.
  Convert to Kotlin `Duration` only on the call side.

## Design system

- Package `me.sdi.ethwallet.ui.designsystem` is the only place that may import Material `Text`, `Button`,
  `OutlinedButton`, `TextButton`, `FilledTonalButton`, `IconButton`, `Card` and `OutlinedTextField`. Screens use
  `EthText`, `EthButton`, `EthIconButton`, `EthCard` and `EthTextField`.
- Text styles are `EthTextStyle.Headline`, `Title`, `Body`, `Caption`. Do not set `fontSize`, `fontWeight` or
  `fontFamily` on a screen. Do not add a style. Pick the nearest one.
- Button styles are `EthButtonStyle.Primary`, `Secondary`, `Text`. Primary is the main action of a screen. Do not add a
  style.
- A card is `EthCard`. Do not set shape, color or elevation on a card. Use `EthCardTone.Error` for failures.

## Presentation logic

- Assume that Start event arrives first. It is forbidden to handle unrealistic events flow like pull-to-refresh before
  OnStart.
- Trace `onEvent` handlers and remove dead branches. Consider only realistic user scenarios: `OnStart` after
  pull-to-refresh or a click on a disabled button are not realistic.
- Concurrency primitives like Lock, synchronized blocks and so on must be treated as overengineering. Agent must provide
  A REALISTIC evidence that concurrent primitive is mandatory.
- ViewModel `init` must be fast and error-safe. Actual initialization (for example initial data loading) must be
  triggered from inside `viewState.onStart {}`.
- ViewModel must not keep user-visible text. Expose errors and messages as an `enum` (or a sealed type). Map them to
  string resources on the Jetpack Compose layer, where localization is available.
- One-time UI effects (shake, toast, navigation) must be sent through `news`. Do not use a state field (for example an
  error id) as an animation trigger.

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
- Collect `viewState` with `collectAsStateWithLifecycle()`, not with `collectAsState()`.
- A state change must not move other UI elements. Keep conditional elements (for example an error `Text`) always in the
  UI tree and control visibility with alpha.

## Security

- A security mechanism must protect against a realistic attacker. Do not add a mechanism that only makes the flow more
  complex and gives an illusion of protection. For example, the app PIN must not take part in key encryption: an
  attacker with access to the app process can brute-force a 6-digit PIN, and the PIN attempts guard does not run then.

## Business logic

- User can have multiple wallets in the app.
- Self transfer is a transfer from one of user wallets to another one. Not only from `wallet1` to `wallet1`. Transfer
  from `wallet1` to `wallet1` high probably never happens.  
