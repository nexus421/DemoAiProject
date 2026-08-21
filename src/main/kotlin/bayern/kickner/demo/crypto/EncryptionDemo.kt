@file:OptIn(ExperimentalKotNexLibAPI::class, CriticalAPI::class)

package bayern.kickner.demo.crypto

import bayern.kickner.klogger.debugLog
import bayern.kickner.klogger.errorLog
import bayern.kickner.klogger.infoLog
import bayern.kickner.klogger.warnLog
import kotnexlib.CriticalAPI
import kotnexlib.ExperimentalKotNexLibAPI
import kotnexlib.crypto.AES
import kotnexlib.crypto.BlowfishEncryption
import kotnexlib.ifFalse
import kotnexlib.toBase64
import kotnexlib.toHexString

/**
 * Showcases KotNexLib's [AES] object (CBC / GCM / ECB) and the legacy [BlowfishEncryption].
 *
 * [AES.GCM] is annotated `@ExperimentalKotNexLibAPI` in KotNexLib 4.3.0 even though it's the
 * *recommended* mode per the library's own docs - hence the file-wide `@OptIn` above.
 * [BlowfishEncryption] is annotated `@CriticalAPI` (opt-in required, kept only for legacy interop).
 */
object EncryptionDemo {

    fun run(secret: String, password: String) {
        infoLog { "--- Encryption: AES (CBC / GCM / ECB) + Blowfish ---" }

        runCbcWithExplicitKey(secret, password)
        val gcmData = runGcmWithPassword(secret, password)
        runAesDataRoundTrip(gcmData, password)
        runEcbLegacy(secret)
        runBlowfishLegacy(secret, password)
    }

    private fun runCbcWithExplicitKey(secret: String, password: String) {
        val salt = AES.Common.generateSecureRandom(16)
        val key = AES.Common.generateSecureAesKeyFromPassword(password, salt)
        val iv = AES.Common.generateIV().getOrThrow()

        debugLog { "CBC: salt(hex)=${salt.toHexString()} salt(base64)=${salt.toBase64()} iv(hex)=${iv.iv.toHexString()}" }

        val encrypted = AES.CBC.encrypt(secret, key, iv).getOrThrow()
        val decrypted = AES.CBC.decrypt(encrypted, key, iv).getOrThrow()
        val roundTripOk = decrypted == secret

        roundTripOk.ifFalse { errorLog("AES/CBC round-trip mismatch!") }
        infoLog { "AES/CBC (explicit key+iv) round-trip ok: $roundTripOk" }
    }

    private fun runGcmWithPassword(secret: String, password: String): AES.AESData {
        val aesData = AES.GCM.encryptWithPassword(secret, password, compress = true)
        val decrypted = aesData.decryptAsString(password).getOrThrow()

        infoLog { "AES/GCM+password (compressed) round-trip ok: ${decrypted == secret}" }
        return aesData
    }

    private fun runAesDataRoundTrip(aesData: AES.AESData, password: String) {
        // AESData.toString()/.restore() bundle salt+iv/nonce+iterations+type into one Base64
        // string that's safe to persist (e.g. in a database column) and later reconstruct.
        val serialized = aesData.toString()
        val restored = AES.AESData.restore(serialized).getOrThrow()
        val decrypted = restored.decryptAsString(password).getOrThrow()

        debugLog { "AESData serialized to a single storable String (${serialized.length} chars)" }
        infoLog { "AESData restore()+decrypt round-trip ok: ${decrypted.isNotEmpty()}" }

        val wrongPassword = restored.decryptAsString("$password-wrong")
        wrongPassword.onFailure {
            errorLog("Decrypting AESData with the wrong password failed as expected", ex = it, printStackTrace = false, sendAsCrash = true)
        }
    }

    private fun runEcbLegacy(secret: String) {
        val key = "LegacyEcbKey1234"
        warnLog { "AES/ECB is deterministic and considered weak - demoing it only for legacy-system reference" }

        val encrypted = AES.ECB.encryptWithPassword(secret, key).getOrThrow()
        val decrypted = AES.ECB.decryptWithPassword(encrypted, key).getOrThrow()

        infoLog { "AES/ECB (legacy) round-trip ok: ${decrypted == secret}" }
    }

    private fun runBlowfishLegacy(secret: String, password: String) {
        warnLog { "Blowfish is a 64-bit-block legacy cipher (@CriticalAPI) - kept only for old-system interop" }

        val encrypted = BlowfishEncryption.encrypt(secret, password)
        val decrypted = encrypted?.let { BlowfishEncryption.decrypt(it, password) }

        infoLog { "Blowfish (legacy) round-trip ok: ${decrypted == secret}" }
    }
}
