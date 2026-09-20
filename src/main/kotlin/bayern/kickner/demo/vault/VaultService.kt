@file:OptIn(ExperimentalKotNexLibAPI::class)

package bayern.kickner.demo.vault

import kotnexlib.ExperimentalKotNexLibAPI
import kotnexlib.ResultOf2
import kotnexlib.ResultOfTripple
import kotnexlib.crypto.AES
import kotnexlib.crypto.Argon2Helper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tiny in-memory user vault. Business outcomes (duplicate username, wrong password, ...) are expected, so they
 * are modeled with [ResultOf2] and [ResultOfTripple] instead of exceptions. `runCatching` is reserved for things
 * that actually throw.
 */
class VaultService {

    /**
     * All registered accounts in registration order. The backing [CopyOnWriteArrayList] makes it safe to expose
     * the list itself instead of a defensive copy: readers iterate over a snapshot even while [register] adds.
     */
    val allUsers: List<UserAccount>
        field = CopyOnWriteArrayList<UserAccount>()

    /**
     * Registers [username] with [password] and stores [secretNote] encrypted. With [useLegacyEcb] the note is
     * encrypted with a fixed AES/ECB key instead of AES/GCM derived from the password.
     *
     * @return the new account, or why the registration was rejected.
     */
    fun register(
        username: String,
        password: CharArray,
        secretNote: String,
        useLegacyEcb: Boolean = false,
    ): ResultOf2<UserAccount, RegistrationError> {
        val alreadyExists = allUsers.any { it.username.equals(username, ignoreCase = true) }
        if (alreadyExists) return ResultOf2.Failure(RegistrationError.UsernameTaken(username))

        val tooWeak = password.size < 8
        if (tooWeak) return ResultOf2.Failure(RegistrationError.PasswordTooWeak("must be at least 8 characters"))

        val hash = Argon2Helper.hash(password)
        val account: UserAccount = if (useLegacyEcb) {
            val key = "LegacyEcbKey1234"
            val encrypted = AES.ECB.encryptWithPassword(secretNote, key).getOrThrow()
            UserAccount.EcbLegacyAccount(username, hash, encrypted, key)
        } else {
            val aesData = AES.GCM.encryptWithPassword(secretNote, String(password), compress = true)
            UserAccount.GcmAccount(username, hash, aesData.toString())
        }

        allUsers.add(account)
        return ResultOf2.Success(account)
    }

    /**
     * Verifies [password] against the stored Argon2id hash. A legacy ECB account logs in with a warning, so the
     * caller can nudge the user towards re-encryption.
     */
    fun login(username: String, password: CharArray): ResultOfTripple<UserAccount, LoginWarning, LoginError> {
        val account = allUsers.find { it.username.equals(username, ignoreCase = true) }
            ?: return ResultOfTripple.Error(LoginError.UnknownUser)

        val verified = Argon2Helper.verify(password, account.argon2Hash).getOrDefault(false)
        if (verified.not()) return ResultOfTripple.Error(LoginError.WrongPassword)

        return if (account is UserAccount.EcbLegacyAccount)
            ResultOfTripple.Warning(LoginWarning(account, "note is encrypted with legacy AES-ECB, re-encrypt it with GCM"))
        else
            ResultOfTripple.Success(account)
    }

    /** Decrypts the account's note, or returns null if [password] does not fit or the stored data is damaged. */
    fun revealSecret(account: UserAccount, password: CharArray): String? = when (account) {
        is UserAccount.GcmAccount -> AES.AESData.restore(account.encryptedNote).getOrNull()
            ?.decryptAsString(String(password))?.getOrNull()

        is UserAccount.EcbLegacyAccount -> AES.ECB.decryptWithPassword(account.encryptedNote, account.ecbKey).getOrNull()
    }
}
