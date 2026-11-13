package com.example.vault.crypto

import com.example.vault.model.EncryptedEnvelope
import com.example.vault.model.VaultPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Provides authenticated encryption and decryption using PBKDF2-HMAC-SHA256 + AES-256-GCM.
 * 100% binary & JSON compatible with the Desktop Java 25 Swing CryptoService.
 */
class CryptoService {

    private val secureRandom = SecureRandom()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encryptPayload(payload: VaultPayload, masterPassword: CharArray): ByteArray {
        if (masterPassword.isEmpty()) {
            throw CryptoException("マスターパスワードが設定されていません。")
        }
        try {
            val plaintextBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)

            val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

            val key = deriveKey(masterPassword, salt, DEFAULT_ITERATIONS)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintextBytes)

            val envelope = EncryptedEnvelope(
                version = 1,
                algorithm = ALGORITHM,
                kdf = KDF_ALGORITHM,
                iterations = DEFAULT_ITERATIONS,
                saltBase64 = Base64.getEncoder().encodeToString(salt),
                ivBase64 = Base64.getEncoder().encodeToString(iv),
                ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext)
            )

            return json.encodeToString(envelope).toByteArray(StandardCharsets.UTF_8)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException("データの暗号化に失敗しました: ${e.message}", e)
        }
    }

    fun decryptPayload(envelopeBytes: ByteArray?, masterPassword: CharArray): VaultPayload {
        if (envelopeBytes == null || envelopeBytes.isEmpty()) {
            return VaultPayload.empty()
        }
        if (masterPassword.isEmpty()) {
            throw CryptoException("マスターパスワードが設定されていません。")
        }
        try {
            val envelopeJson = String(envelopeBytes, StandardCharsets.UTF_8)
            val envelope = json.decodeFromString<EncryptedEnvelope>(envelopeJson)
            val iterations = if (envelope.iterations > 0) envelope.iterations else DEFAULT_ITERATIONS

            val salt = Base64.getDecoder().decode(envelope.saltBase64)
            val iv = Base64.getDecoder().decode(envelope.ivBase64)
            val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)

            val key = deriveKey(masterPassword, salt, iterations)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintextBytes = cipher.doFinal(ciphertext)

            val payloadJson = String(plaintextBytes, StandardCharsets.UTF_8)
            return json.decodeFromString<VaultPayload>(payloadJson)
        } catch (e: AEADBadTagException) {
            throw CryptoException("復号に失敗しました。マスターパスワードが誤っているか、データが破損しています。", e)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException("暗号化データの読み込みに失敗しました: ${e.message}", e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val ALGORITHM = "AES-256-GCM"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 310_000
        const val SALT_LENGTH_BYTES = 16
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val KEY_BITS = 256
    }
}
