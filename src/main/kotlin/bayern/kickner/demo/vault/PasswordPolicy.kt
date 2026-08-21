package bayern.kickner.demo.vault

import bayern.kickner.klogger.errorLog
import bayern.kickner.klogger.warnLog
import kotnexlib.crypto.Argon2Helper

object PasswordPolicy {

    /**
     * try/catch instead of runCatching: the two exception paths need genuinely different
     * recovery (silently downgrade to safe defaults vs. rethrow), which reads clearer here
     * than a runCatching/fold chain would.
     */
    fun hashWithFallback(password: CharArray, iterations: Int, memoryKb: Int): String {
        return try {
            Argon2Helper.hash(password, iterations = iterations, memoryKb = memoryKb)
        } catch (e: IllegalArgumentException) {
            warnLog { "Argon2 parameters rejected (${e.message}) - falling back to library defaults" }
            Argon2Helper.hash(password)
        } catch (e: Exception) {
            errorLog("Unexpected error while hashing password", ex = e, sendAsCrash = true)
            throw e
        }
    }
}
