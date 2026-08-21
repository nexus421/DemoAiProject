package bayern.kickner.demo

import bayern.kickner.demo.crypto.EncryptionDemo
import bayern.kickner.demo.crypto.HashingDemo
import bayern.kickner.demo.logging.DemoLogging
import bayern.kickner.demo.vault.LoginError
import bayern.kickner.demo.vault.PasswordPolicy
import bayern.kickner.demo.vault.RegistrationError
import bayern.kickner.demo.vault.UserAccount
import bayern.kickner.demo.vault.cipherLabel
import bayern.kickner.demo.vault.VaultReport
import bayern.kickner.demo.vault.VaultService
import bayern.kickner.klogger.KLogger
import bayern.kickner.klogger.debugLog
import bayern.kickner.klogger.errorLog
import bayern.kickner.klogger.infoLog
import bayern.kickner.klogger.staticLog
import bayern.kickner.klogger.warnLog
import kotnexlib.ArgsInterpreter
import kotnexlib.ResultOf2
import kotnexlib.ResultOfTripple
import kotnexlib.coverString
import kotnexlib.measureTime

/**
 * main() is a top-level/static context, so it has no `this` and can't use the debugLog/infoLog/...
 * receiver extensions (they need DEFAULT_LOG_TAG from a receiver). [staticLog] is what Klogger
 * provides for exactly this case. Everything below main() runs inside [DemoRunner] instead, where
 * the normal receiver-based helpers work and tag each line with the calling class automatically.
 */
fun main(args: Array<String>) {
    DemoLogging.configure()
    staticLog(KLogger.Level.INFO, "Main") { "=== KotNexLib & Klogger reference demo starting ===" }

    val interpreter = ArgsInterpreter(args)
    val username = interpreter.getValue("user") ?: "ada.lovelace"
    val password = interpreter.getValue("password") ?: "correct horse battery staple"

    val (_, totalMs) = measureTime {
        runCatching { DemoRunner.run(username, password) }
            .onFailure { staticLog(KLogger.Level.CRASH, "Main") { "Demo aborted: ${it.stackTraceToString()}" } }
    }

    staticLog(KLogger.Level.INFO, "Main") {
        "=== Demo finished in ${totalMs}ms - ${DemoLogging.alerts} alert(s), ${DemoLogging.audit.size} audit entries captured ==="
    }
}

private object DemoRunner {

    fun run(username: String, password: String) {
        infoLog { "Running full scenario for demo user '$username'" }

        HashingDemo.run(password)
        EncryptionDemo.run(secret = "Room 237 access code: 4711-ALPHA", password = password)

        val policyHash = PasswordPolicy.hashWithFallback(password.toCharArray(), iterations = 1, memoryKb = 512)
        debugLog { "Policy-checked Argon2 hash sample: ${policyHash.take(24)}…" }

        val vault = VaultService()
        runRegistrations(vault, username, password)
        runLogins(vault, username, password)
        VaultReport.print(vault.allUsers)
    }

    private fun runRegistrations(vault: VaultService, username: String, password: String) {
        infoLog { "--- Registration: ResultOf2<UserAccount, RegistrationError> ---" }

        val secretNote = "Zurich vault 8017-4711, launch codes: 1349"

        logRegistration(username, vault.register(username, password.toCharArray(), secretNote))
        logRegistration(username, vault.register(username, password.toCharArray(), secretNote)) // duplicate
        logRegistration("intern", vault.register("intern", "123".toCharArray(), "n/a")) // too weak

        val legacyUser = "legacy.$username"
        logRegistration(legacyUser, vault.register(legacyUser, password.toCharArray(), secretNote, useLegacyEcb = true))
    }

    private fun logRegistration(username: String, result: ResultOf2<UserAccount, RegistrationError>) {
        when (result) {
            is ResultOf2.Success -> infoLog { "Registered '$username' (${result.value.cipherLabel})" }
            is ResultOf2.Failure -> when (val error = result.value) {
                is RegistrationError.UsernameTaken -> warnLog { "Registration rejected: username '${error.username}' already taken" }
                is RegistrationError.PasswordTooWeak -> warnLog { "Registration rejected for '$username': ${error.reason}" }
            }
        }
    }

    private fun runLogins(vault: VaultService, username: String, password: String) {
        infoLog { "--- Login: ResultOfTripple<UserAccount, LoginWarning, LoginError> ---" }

        attemptLogin(vault, username, password)
        attemptLogin(vault, username, "definitely-wrong-password")
        attemptLogin(vault, "ghost.$username", password)
        attemptLogin(vault, "legacy.$username", password)
    }

    private fun attemptLogin(vault: VaultService, username: String, password: String) {
        when (val result = vault.login(username, password.toCharArray())) {
            is ResultOfTripple.Success -> {
                infoLog { "Login ok for '$username'" }
                revealAndLog(vault, result.value, password)
            }

            is ResultOfTripple.Warning -> {
                warnLog { "Login ok for '$username' but: ${result.value.reason}" }
                revealAndLog(vault, result.value.account, password)
            }

            is ResultOfTripple.Error -> when (val error = result.value) {
                LoginError.UnknownUser -> warnLog { "Login failed for '$username': unknown user" }
                LoginError.WrongPassword -> errorLog("Login failed for '$username': wrong password")
            }
        }
    }

    private fun revealAndLog(vault: VaultService, account: UserAccount, password: String) {
        val secret = vault.revealSecret(account, password.toCharArray())
        if (secret == null) return errorLog("Could not decrypt secret note for '${account.username}'", sendAsCrash = true)

        infoLog { "Secret note for '${account.username}': ${secret.coverString(6, secret.length - 7)}" }
    }
}
