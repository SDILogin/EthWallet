package me.sdi.ethwallet.core.security

/** The user dismissed the system authentication (Touch ID or account password) that guards the master KEK. */
class UserPresenceCancelledException : IllegalStateException("User presence check was cancelled")
