package bayern.kickner.demo.crypto

import bayern.kickner.klogger.debugLog
import bayern.kickner.klogger.infoLog
import kotnexlib.coverString
import kotnexlib.crypto.HashAlgorithm
import kotnexlib.crypto.HashAlgorithmBC
import kotnexlib.crypto.hash
import kotnexlib.crypto.hashBC
import kotnexlib.crypto.hashIter
import kotnexlib.measureTime

/**
 * Shows KotNexLib's two independent hashing paths: the plain JVM [java.security.MessageDigest] via [hash] and
 * BouncyCastle via [hashBC]. Neither is suitable for passwords. For those see
 * [bayern.kickner.demo.vault.PasswordPolicy], which uses Argon2id.
 */
object HashingDemo {

    /** Hashes [password] with both paths and once iteratively, logging the digests. */
    fun run(password: String) {
        infoLog { "--- Hashing: plain JVM (MessageDigest) + BouncyCastle ---" }

        val md5 = password.hash(HashAlgorithm.MD5)
        val sha256 = password.hash(HashAlgorithm.SHA_256)
        val sha3_256 = password.hashBC(HashAlgorithmBC.SHA3_256)
        val sha3_512 = password.hashBC(HashAlgorithmBC.SHA3_512)

        debugLog { "MD5      (JVM):  $md5" }
        debugLog { "SHA-256  (JVM):  $sha256" }
        debugLog { "SHA3-256 (BC):   $sha3_256" }
        debugLog { "SHA3-512 (BC):   $sha3_512" }

        val (iterated, iterMs) = measureTime { password.hashIter(HashAlgorithm.SHA_256, iterations = 20_000) }
        infoLog { "hashIter (20k rounds, ${iterMs}ms): ${iterated.coverString(8, iterated.length - 9)}" }
    }
}
