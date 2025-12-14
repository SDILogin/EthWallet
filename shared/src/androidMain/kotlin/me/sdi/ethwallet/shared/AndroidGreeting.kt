package me.sdi.ethwallet.shared

import android.os.Build

actual fun platformName(): String {
    return "Android ${Build.VERSION.SDK_INT}"
}
