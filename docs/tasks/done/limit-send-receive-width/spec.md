# Task

#337 Limit the width of send and receive screens

On wide windows (Desktop, tablet, foldable), screen content must not stretch to the full pane width.
All content on the Send and Receive screens is at most 420 dp wide and centered.
The phone layout does not change.

## Scope

- Receive: the QR code and the address row get the 420 dp limit. Before, the address row and its click
  area used the full pane width.
- Send: no change. All content already has the 420 dp limit. `EthButtonsLayout` already centers and limits
  the Send button.
- The `@FormFactorPreviews` of both screens already cover Phone, Foldable, Tablet and Desktop.

## Out of scope

- `collectAsState()` in both screens.
- Other screens.
- Error texts under the Send and Receive fields appear only on error and move the content below.
