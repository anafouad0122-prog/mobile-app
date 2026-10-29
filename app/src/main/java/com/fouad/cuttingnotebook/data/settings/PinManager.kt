package com.fouad.cuttingnotebook.data.settings

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The stored proof of a PIN. Base64 fields so they survive DataStore as plain strings. */
data class PinVerifier(
    val hash: String,
    val salt: String,
    val iterations: Int,
)

enum class PinError {
    TOO_SHORT,
    TOO_LONG,
    NOT_DIGITS,

    /** The two entries on the setup or change form do not match. */
    MISMATCH,

    /** Wrong PIN at the lock screen, or in a confirmation prompt. */
    WRONG,

    /** Wrong PIN in the "current PIN" field when changing it. */
    WRONG_CURRENT,

    NOT_SET,
}

/**
 * PIN hashing.
 *
 * The PIN is never written anywhere — only a PBKDF2-HMAC-SHA256 hash of it with a random
 * per-install salt (spec 13). The verifier is also kept out of the backup file, so a backup
 * that leaves the phone cannot be used to recover the PIN.
 */
object PinManager {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun validateFormat(pin: String): PinError? = when {
        pin.length < MIN_LENGTH -> PinError.TOO_SHORT
        pin.length > MAX_LENGTH -> PinError.TOO_LONG
        !pin.all { it.isDigit() } -> PinError.NOT_DIGITS
        else -> null
    }

    fun createVerifier(pin: String): PinVerifier {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PinVerifier(
            hash = encode(derive(pin, salt, ITERATIONS)),
            salt = encode(salt),
            iterations = ITERATIONS,
        )
    }

    fun verify(pin: String, verifier: PinVerifier): Boolean {
        val salt = decode(verifier.salt) ?: return false
        val expected = decode(verifier.hash) ?: return false
        val actual = derive(pin, salt, verifier.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray? =
        runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()
}
