# Task

Prevent the keyboard from learning, caching, or syncing the recovery phrase that the user types on the import
wallet screen.

## Background

- `ImportWalletScreen.kt` uses an `OutlinedTextField` for the recovery phrase with `KeyboardOptions.Default`
  (only `imeAction = ImeAction.Done` is changed). Autocorrect and suggestions stay on and the input is reported to
  the keyboard as ordinary text.
- With ordinary text input, a keyboard may add the seed words to its personal dictionary, show them as suggestions
  later, and sync that dictionary to the keyboard vendor's cloud. Third-party keyboards have full access to
  everything typed into such a field.
- OWASP MASTG: MASTG-KNOW-0055 "Keyboard Cache", MASTG-BEST-0019 "Use Non-Caching Input Types for Sensitive
  Fields". For Compose, MASTG recommends `keyboardOptions` that disable suggestions and use a password keyboard type.

## Scope

- Recovery phrase field on `ImportWalletScreen`: configure `KeyboardOptions` with `KeyboardType.Password` and
  `autoCorrectEnabled = false`, keeping `imeAction = ImeAction.Done`. Keep the text visible (no
  `PasswordVisualTransformation`): the user must be able to check the words they typed.
- Check whether Compose can also set `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` on this field. Use it if it can
  be done without a custom `TextInputService`; otherwise document why not.
- Check every other text input that can receive a secret (seed phrase, private key, PIN). The PIN uses the in-app
  keypad and does not open the system keyboard; confirm that this is still true.
- Desktop has no system keyboard learning; no Desktop change is expected. iOS has no source set yet.
- Out of scope: blocking screenshots (accepted as is) and overlay or accessibility protection.

## Definition of done

- Robolectric or Compose UI test asserting that the recovery phrase field reports a password input type with
  autocorrect disabled (for example through the `EditorInfo` created for the focused field).
- Existing import wallet tests still pass: pasting and typing a phrase, validation, and import.
- Manual check on Android with Gboard: type a recovery phrase, then type its first letters in another app and
  confirm the seed words are not suggested.
- Formatting and the relevant app and shared tests pass.
