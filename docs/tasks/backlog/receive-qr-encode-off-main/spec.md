# Task

#326 Encode the receive QR code on the Default dispatcher

## Background

`QRCodeWriter().encode` runs in `LaunchedEffect(qrData)`. `LaunchedEffect` runs on the UI dispatcher, so the encode
runs on the main thread. It runs again for each distinct valid amount that the user types on the Receive screen.

- `shared/src/androidMain/kotlin/me/sdi/ethwallet/ui/receive/QrCodeCanvas.kt:35`
- `shared/src/desktopMain/kotlin/me/sdi/ethwallet/ui/receive/QrCodeCanvas.kt:36`

The matrix is 150 or 300 px, not the module grid. The `Canvas` loop checks 22,500 or 90,000 cells and calls one
`drawRect` for each dark cell on each draw.

Estimated severity: medium to high. Platforms: Android and Desktop.

Found in GitHub PR #27 for #10 Find long-running main thread operations; set up profiler and StrictMode.
Static review only. Nothing was measured. Severity: high is more than 16 ms on a frame. Medium grows with data size
or runs often. Low is a few ms or less, or one time at startup.

## Scope

- Call `QRCodeWriter.encode` on `Dispatchers.Default` on Android and Desktop.
- Encode the module grid, not a pixel matrix, so that the `Canvas` loop draws one rectangle for each module.

## Definition of done

- `QRCodeWriter.encode` does not run on the main thread.
- The QR code looks the same as before on Android and Desktop.
- `./gradlew spotlessApply` and the app and desktop tests pass.
