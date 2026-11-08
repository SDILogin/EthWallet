# Task

Mark the views that show or accept secrets, and the transfer confirmation, as accessibility data sensitive, so
that accessibility services that are not real accessibility tools cannot read them or act on them.

## Background

- Android 14 (API 34) and higher hides views marked as accessibility data sensitive from accessibility services
  that do not declare `android:isAccessibilityTool="true"`. Such services, including malware the user was tricked
  into enabling, cannot read the content of these views or interact with them. On Android 16 (API 36) and higher
  they also cannot inject gestures that target them.
- No view in the app is marked today. A malicious accessibility service can read the recovery phrase, watch the
  PIN being entered on the in-app keypad, and tap the send button on the user's behalf.
- OWASP MASTG: MASTG-BEST-0075 "Restricting Accessibility Service Access to Sensitive Views" (see also
  MASTG-KNOW-0143 and MASTG-BEST-0040).

## Scope

Mark these views with `Modifier.semantics { isSensitiveData = true }` (Compose) on the smallest container that
covers the sensitive content, not on whole screens:

- Recovery phrase display: `MnemonicCard` (`CreateWalletScreen`).
- Recovery phrase input: the recovery phrase field on `ImportWalletScreen`.
- PIN entry: `PinCodeInput` and the keypads (`PinNumberPad`, the keypad in `VerifyPinCodeScreen`) on the set,
  verify, and change PIN screens.
- Transfer confirmation: the send button and the recipient and amount fields on `SendScreen`.

Also:

- Check that `isSensitiveData` is available in the project's Compose Multiplatform version for `commonMain`. If it
  is not, add an Android-only modifier through `expect`/`actual` (no-op on Desktop) and note the version that would
  remove the workaround.
- Do not set `ACCESSIBILITY_DATA_SENSITIVE_NO` anywhere.
- Out of scope: overlay or tapjacking protection with `filterTouchesWhenObscured` (MASTG-BEST-0040), screenshots
  (accepted as is), and Desktop, which has no equivalent.

## Accessibility impact

- TalkBack and other services that declare `isAccessibilityTool="true"` keep full access. The marked views must
  remain usable with TalkBack: the PIN keypad buttons must still be announced and activatable.
- Password managers and automation apps lose access to the marked views. That is acceptable here.
- Devices below Android 14 ignore the flag; there is no platform fallback.

## Definition of done

- Compose UI tests asserting that each listed view exposes the sensitive-data semantics property.
- Existing UI tests still pass.
- Manual check on an Android 14+ device: TalkBack can still create a wallet, enter a PIN, and send a transfer; an
  accessibility service without `isAccessibilityTool` cannot read the recovery phrase.
- Formatting and the relevant app and shared tests pass.
