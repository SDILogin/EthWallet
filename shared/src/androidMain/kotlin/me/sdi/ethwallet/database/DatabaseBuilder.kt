package me.sdi.ethwallet.database

import android.content.Context

object DatabaseBuilder {
    fun getInstance(context: Context): AppDatabase = AppDatabase.getInstance(context)
}
