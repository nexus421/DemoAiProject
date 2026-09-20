package bayern.kickner.demo.vault

/**
 * A registered user. Modeled as a sealed interface instead of one flat data class with a nullable `ecbKey`, so an
 * ECB account without a key cannot be constructed: illegal states are unrepresentable instead of being checked at
 * runtime.
 *
 * @property username Unique name, compared case-insensitively.
 * @property argon2Hash Argon2id hash of the password, self-contained (salt and parameters included).
 */
sealed interface UserAccount {
    val username: String
    val argon2Hash: String

    /** @property encryptedNote The secret note as serialized `AES.AESData`, encrypted with the user's password. */
    data class GcmAccount(
        override val username: String,
        override val argon2Hash: String,
        val encryptedNote: String,
    ) : UserAccount

    /**
     * @property encryptedNote The secret note, AES/ECB encrypted with [ecbKey].
     * @property ecbKey Fixed legacy key, 16 or 32 characters as AES/ECB requires.
     */
    data class EcbLegacyAccount(
        override val username: String,
        override val argon2Hash: String,
        val encryptedNote: String,
        val ecbKey: String,
    ) : UserAccount
}

/** Human-readable name of the cipher protecting the account's note. */
val UserAccount.cipherLabel: String
    get() = when (this) {
        is UserAccount.GcmAccount -> "AES-GCM"
        is UserAccount.EcbLegacyAccount -> "AES-ECB (legacy)"
    }

/**
 * Payload of [kotnexlib.ResultOfTripple]'s Warning branch: the login succeeded, but the account needs attention.
 *
 * @property account The account that logged in.
 * @property reason What needs attention, for the log.
 */
data class LoginWarning(val account: UserAccount, val reason: String)

/** Expected reasons for a rejected registration, returned instead of thrown. */
sealed interface RegistrationError {
    data class UsernameTaken(val username: String) : RegistrationError
    data class PasswordTooWeak(val reason: String) : RegistrationError
}

/** Expected reasons for a failed login, returned instead of thrown. */
sealed interface LoginError {
    data object UnknownUser : LoginError
    data object WrongPassword : LoginError
}
