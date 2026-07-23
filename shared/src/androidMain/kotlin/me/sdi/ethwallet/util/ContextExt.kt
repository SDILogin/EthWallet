package me.sdi.ethwallet.util

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private const val PREFERENCES_DATASTORE_NAME = "secure_pin_preferences"
val Context.preferencesDataStore by preferencesDataStore(name = PREFERENCES_DATASTORE_NAME)
