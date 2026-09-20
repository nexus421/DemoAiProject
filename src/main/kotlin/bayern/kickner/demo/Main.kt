package bayern.kickner.demo

import bayern.kickner.demo.crypto.EncryptionDemo
import bayern.kickner.demo.crypto.HashingDemo
import bayern.kickner.demo.logging.DemoLogging
import bayern.kickner.demo.vault.LoginError
import bayern.kickner.demo.vault.PasswordPolicy
import bayern.kickner.demo.vault.RegistrationError
import bayern.kickner.demo.vault.UserAccount
import bayern.kickner.demo.vault.VaultReport
import bayern.kickner.demo.vault.VaultService
import bayern.kickner.demo.vault.cipherLabel
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "Main"

/**
 * Format of every timestamp this project prints, e.g. `19.09.2026 18:40:12 CEST`. Pinned to [Locale.ENGLISH] so the
 * zone abbreviation stays the same on every host.
 */
internal val timestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z", Locale.ENGLISH)

/** Version and build time for the start banner, e.g. `1.0.0 (built 20.09.2026 13:11:02 CEST)`. */
internal val appVersion: String =
    "${BuildConfig.VERSION} (built ${Instant.ofEpochMilli(BuildConfig.BUILD_TIME).atZone(ZoneId.systemDefault()).format(timestampFormat)})"

/**
 * Entry point. Configures Klogger, then runs the whole scenario for one demo user: hashing, encryption, vault
 * registrations and logins, and the final report. The arguments are `key=value` pairs: `user=<name>` and
 * `password=<secret>` override the built-in defaults.
 */
fun main(args: Array<String>) {
    DemoLogging.configure()
    // A top-level function has no receiver and therefore no default log tag, so it logs through staticLog
    staticLog(KLogger.Level.INFO, TAG) { "=== KotNexLib & Klogger reference demo $appVersion starting ===" }

    val arguments = ArgsInterpreter(args)
    val username = arguments.getValue("user") ?: "ada.lovelace"
    val password = arguments.getValue("password") ?: "correct horse battery staple"

    val (_, totalMs) = measureTime {
        runCatching { DemoRunner.run(username, password) }
            .onFailure { staticLog(KLogger.Level.CRASH, TAG) { "Demo aborted: ${it.stackTraceToString()}" } }
    }

    staticLog(KLogger.Level.INFO, TAG) {
        "=== Demo finished in ${totalMs}ms with ${DemoLogging.alerts} alert(s) and ${DemoLogging.audit.size} audit entries ==="
    }
}

/** Runs the scenario. Inside an object the receiver-based log helpers work and tag every line with `DemoRunner`. */
private object DemoRunner {

    fun run(username: String, password: String) {
        infoLog { "Running full scenario for demo user '$username'" }

        HashingDemo.run(password)
        EncryptionDemo.run(secret = "Room 237 access code: 4711-ALPHA", password = password)

        val policyHash = PasswordPolicy.hashWithFallback(password.toCharArray(), iterations = 1, memoryKb = 512)
        debugLog { "Argon2 hash after the policy fallback (first 24 chars): ${policyHash.take(24)}" }

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

            is ResultOfTripple.Error -> when (result.value) {
                LoginError.UnknownUser -> warnLog { "Login failed for '$username': unknown user" }
                LoginError.WrongPassword -> errorLog("Login failed for '$username': wrong password")
            }
        }
    }

    private fun revealAndLog(vault: VaultService, account: UserAccount, password: String) {
        val secret = vault.revealSecret(account, password.toCharArray())
        if (secret == null) {
            errorLog("Could not decrypt secret note for '${account.username}'", sendAsCrash = true)
            return
        }

        infoLog { "Secret note for '${account.username}': ${secret.coverString(6, secret.length - 7)}" }
    }
}
