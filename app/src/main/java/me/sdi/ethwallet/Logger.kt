package me.sdi.ethwallet

enum class Level {
    DEBUG,
    WARN,
}

typealias Tag = String
typealias Message = String

object Logger {
    var enabled: Boolean = false
    var printer: (Level, Tag, Message) -> Unit = { level, tag, message ->
        when (level) {
            Level.DEBUG -> println("DEBUG: [$tag] $message")
            Level.WARN -> println("WARN: [$tag] $message")
        }
    }

    fun warn(tag: String, message: () -> String) {
        if (enabled) {
            printer(Level.WARN, tag, message())
        }
    }

    fun debug(tag: String, message: () -> String) {
        if (enabled) {
            printer(Level.DEBUG, tag, message())
        }
    }
}
