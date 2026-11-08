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
