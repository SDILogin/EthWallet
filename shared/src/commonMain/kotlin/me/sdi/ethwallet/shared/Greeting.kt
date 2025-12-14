package me.sdi.ethwallet.shared

class Greeting {
    fun greet(): String {
        return "Hello, from shared!"
    }
}

expect fun platformName(): String
