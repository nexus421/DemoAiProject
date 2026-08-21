@file:OptIn(ExperimentalKotNexLibAPI::class)

package bayern.kickner.demo.vault

import kotnexlib.ExperimentalKotNexLibAPI
import kotnexlib.ResultOf2
import kotnexlib.ResultOfTripple
import kotnexlib.crypto.AES
import kotnexlib.crypto.Argon2Helper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tiny in-memory user vault. Business outcomes (duplicate username, wrong password, ...) are
 * modeled with [ResultOf2]/[ResultOfTripple] rather than exceptions - they are expected outcomes,
 * not failures, so `runCatching` would be the wrong tool here.
 */
class VaultService {

    // Backed by CopyOnWriteArrayList (not a plain mutableListOf()) specifically so exposing it
    // directly as List<UserAccount> below is safe without a defensive .toList() copy: readers get
    // a stable snapshot even if register() mutates the list concurrently.
    val allUsers: List<UserAccount>
        field = CopyOnWriteArrayList<UserAccount>()

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

    fun login(username: String, password: CharArray): ResultOfTripple<UserAccount, LoginWarning, LoginError> {
        val account = allUsers.find { it.username.equals(username, ignoreCase = true) }
            ?: return ResultOfTripple.Error(LoginError.UnknownUser)

        val verified = Argon2Helper.verify(password, account.argon2Hash).getOrDefault(false)
        if (verified.not()) return ResultOfTripple.Error(LoginError.WrongPassword)

        return if (account is UserAccount.EcbLegacyAccount)
            ResultOfTripple.Warning(LoginWarning(account, "legacy AES-ECB note encryption - re-encrypt with GCM"))
        else
            ResultOfTripple.Success(account)
    }

    fun revealSecret(account: UserAccount, password: CharArray): String? = when (account) {
        is UserAccount.GcmAccount -> AES.AESData.restore(account.encryptedNote).getOrNull()
            ?.decryptAsString(String(password))?.getOrNull()

        is UserAccount.EcbLegacyAccount -> AES.ECB.decryptWithPassword(account.encryptedNote, account.ecbKey).getOrNull()
    }
}
