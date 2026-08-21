package bayern.kickner.demo.vault

/**
 * A registered user. Modeled as a sealed interface (not one flat data class with a nullable
 * `ecbKey`) so an ECB account without a key simply cannot be constructed - illegal states are
 * unrepresentable instead of being guarded against at runtime.
 */
sealed interface UserAccount {
    val username: String
    val argon2Hash: String

    data class GcmAccount(
        override val username: String,
        override val argon2Hash: String,
        val encryptedNote: String,
    ) : UserAccount

    data class EcbLegacyAccount(
        override val username: String,
        override val argon2Hash: String,
        val encryptedNote: String,
        val ecbKey: String,
    ) : UserAccount
}

val UserAccount.cipherLabel: String
    get() = when (this) {
        is UserAccount.GcmAccount -> "AES-GCM"
        is UserAccount.EcbLegacyAccount -> "AES-ECB (legacy)"
    }

/** Payload for [kotnexlib.ResultOfTripple]'s Warning branch: the login still succeeded, but... */
data class LoginWarning(val account: UserAccount, val reason: String)

sealed interface RegistrationError {
    data class UsernameTaken(val username: String) : RegistrationError
    data class PasswordTooWeak(val reason: String) : RegistrationError
}

sealed interface LoginError {
    data object UnknownUser : LoginError
    data object WrongPassword : LoginError
}
