package com.homelab.app.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    // Constants
    private val MAGIC = byteArrayOf(0x48, 0x4C, 0x41, 0x42) // "HLAB"
    private const val FORMAT_VERSION: Byte = 1
    private const val HEADER_SIZE = 4 + 1 + 16 + 12 // magic + version + salt + nonce = 33 bytes
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val PBKDF2_ITERATIONS = 600_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128

    // Errors
    class InvalidFileFormatException : Exception("Invalid backup file format.")
    class UnsupportedVersionException : Exception("Unsupported backup file version.")
    class DecryptionFailedException : Exception("Decryption failed. Wrong password?")

    /**
     * Encrypts plaintext data with a password and returns the full .homelab binary payload.
     */
    fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = randomBytes(SALT_LENGTH)
        val nonce = randomBytes(NONCE_LENGTH)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertextAndTag = cipher.doFinal(data)

        val output = ByteArray(HEADER_SIZE + ciphertextAndTag.size)
        // 0..3: magic
        System.arraycopy(MAGIC, 0, output, 0, MAGIC.size)
        // 4: version
        output[4] = FORMAT_VERSION
        // 5..20: salt
        System.arraycopy(salt, 0, output, 5, SALT_LENGTH)
        // 21..32: nonce
        System.arraycopy(nonce, 0, output, 21, NONCE_LENGTH)
        // 33..end: ciphertext & tag
        System.arraycopy(ciphertextAndTag, 0, output, 33, ciphertextAndTag.size)

        return output
    }

    /**
     * Decrypts a .homelab binary payload with a password and returns the plaintext data.
     */
    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (data.size <= HEADER_SIZE) {
            throw InvalidFileFormatException()
        }

        // Validate magic
        for (i in 0 until 4) {
            if (data[i] != MAGIC[i]) {
                throw InvalidFileFormatException()
            }
        }

        // Validate version
        val version = data[4]
        if (version != FORMAT_VERSION) {
            throw UnsupportedVersionException()
        }

        // Extract components
        val salt = ByteArray(SALT_LENGTH)
        System.arraycopy(data, 5, salt, 0, SALT_LENGTH)

        val nonce = ByteArray(NONCE_LENGTH)
        System.arraycopy(data, 21, nonce, 0, NONCE_LENGTH)

        val ciphertextAndTag = ByteArray(data.size - HEADER_SIZE)
        System.arraycopy(data, 33, ciphertextAndTag, 0, ciphertextAndTag.size)

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

        return try {
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            cipher.doFinal(ciphertextAndTag)
        } catch (e: Exception) {
            throw DecryptionFailedException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun randomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}
