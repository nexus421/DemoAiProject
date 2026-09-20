package bayern.kickner.demo.vault

import bayern.kickner.klogger.errorLog
import bayern.kickner.klogger.warnLog
import kotnexlib.crypto.Argon2Helper

/** Argon2id password hashing with a guard against parameters the library rejects. */
object PasswordPolicy {

    /**
     * Hashes [password] with Argon2id. Parameters the library rejects (too few [iterations], too little [memoryKb])
     * are replaced by its defaults. Anything else is logged and rethrown.
     *
     * This is the one deliberate `try`/`catch` in the project: the two exception paths need different recovery
     * (silently downgrade versus rethrow), which reads clearer than a `runCatching`/`fold` chain would.
     */
    fun hashWithFallback(password: CharArray, iterations: Int, memoryKb: Int): String {
        return try {
            Argon2Helper.hash(password, iterations = iterations, memoryKb = memoryKb)
        } catch (e: IllegalArgumentException) {
            warnLog { "Argon2 parameters rejected (${e.message}), falling back to library defaults" }
            Argon2Helper.hash(password)
        } catch (e: Exception) {
            errorLog("Unexpected error while hashing password", ex = e, sendAsCrash = true)
            throw e
        }
    }
}
