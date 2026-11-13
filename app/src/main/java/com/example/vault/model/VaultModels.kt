package com.example.vault.model

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Represents a single text entry inside the encrypted vault.
 * JSON field names and formats match the Desktop Java 25 Swing application 100%.
 */
@Serializable
data class TextEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val contentHash: String = computeSha256Short(content),
    val createdAt: String = Instant.now().toString()
) {
    fun createdAtInstant(): Instant = try {
        Instant.parse(createdAt)
    } catch (_: Exception) {
        Instant.now()
    }

    /**
     * Formats the dropdown label as:
     * - "yyyy-MM-dd HH:mm:ss | <title>" when title is provided
     * - "yyyy-MM-dd HH:mm:ss | #<12-char-sha256>" when title is blank
     */
    fun displayLabel(): String {
        val timestampStr = TIMESTAMP_FORMATTER.format(createdAtInstant())
        val trimmedTitle = title.trim()
        val hash = contentHash.ifBlank { computeSha256Short(content) }
        val labelPart = if (trimmedTitle.isEmpty()) "#$hash" else trimmedTitle
        return "$timestampStr | $labelPart"
    }

    companion object {
        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        fun create(title: String, content: String): TextEntry {
            val normalizedTitle = title.trim()
            return TextEntry(
                id = UUID.randomUUID().toString(),
                title = normalizedTitle,
                content = content,
                contentHash = computeSha256Short(content),
                createdAt = Instant.now().toString()
            )
        }

        /**
         * Computes the first 12 lowercase hex characters (6 bytes) of the SHA-256 digest of [text].
         */
        fun computeSha256Short(text: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(text.toByteArray(StandardCharsets.UTF_8))
            return digest.take(6).joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Plaintext container serialized inside the AES-256-GCM ciphertext.
 */
@Serializable
data class VaultPayload(
    val version: Int = 1,
    val entries: List<TextEntry> = emptyList()
) {
    fun withAddedEntry(newEntry: TextEntry): VaultPayload {
        val updated = (listOf(newEntry) + entries)
            .sortedByDescending { it.createdAtInstant() }
        return copy(entries = updated)
    }

    fun withRemovedEntry(id: String): VaultPayload {
        return copy(entries = entries.filterNot { it.id == id })
    }

    companion object {
        fun empty() = VaultPayload(version = 1, entries = emptyList())
    }
}

/**
 * Self-describing encrypted envelope stored on Google Drive, Dropbox, or Local File.
 * Matches the exact JSON structure produced by the Desktop Java 25 application.
 */
@Serializable
data class EncryptedEnvelope(
    val version: Int = 1,
    val algorithm: String = "AES-256-GCM",
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 310_000,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String
)

enum class StorageType(val displayName: String) {
    GOOGLE_DRIVE("Google Drive API"),
    DROPBOX("Dropbox API"),
    LOCAL_FILE("ローカルファイル");

    override fun toString(): String = displayName
}

@Serializable
data class AppConfig(
    val activeStorageType: StorageType = StorageType.GOOGLE_DRIVE,
    // Google Drive API settings
    val googleAccessToken: String = "",
    val googleClientId: String = "",
    val googleClientSecret: String = "",
    val googleRefreshToken: String = "",
    val googleFolderId: String = "",
    val googleFileName: String = DEFAULT_FILE_NAME,
    // Dropbox API settings
    val dropboxAccessToken: String = "",
    val dropboxAppKey: String = "",
    val dropboxAppSecret: String = "",
    val dropboxRefreshToken: String = "",
    val dropboxFilePath: String = "/$DEFAULT_FILE_NAME",
    // Local file name
    val localFileName: String = DEFAULT_FILE_NAME
) {
    fun normalized(): AppConfig = copy(
        googleAccessToken = googleAccessToken.trim(),
        googleClientId = googleClientId.trim(),
        googleClientSecret = googleClientSecret.trim(),
        googleRefreshToken = googleRefreshToken.trim(),
        googleFolderId = googleFolderId.trim(),
        googleFileName = googleFileName.trim().ifEmpty { DEFAULT_FILE_NAME },
        dropboxAccessToken = dropboxAccessToken.trim(),
        dropboxAppKey = dropboxAppKey.trim(),
        dropboxAppSecret = dropboxAppSecret.trim(),
        dropboxRefreshToken = dropboxRefreshToken.trim(),
        dropboxFilePath = dropboxFilePath.trim().let {
            when {
                it.isEmpty() -> "/$DEFAULT_FILE_NAME"
                it.startsWith("/") -> it
                else -> "/$it"
            }
        },
        localFileName = localFileName.trim().ifEmpty { DEFAULT_FILE_NAME }
    )

    companion object {
        const val DEFAULT_FILE_NAME = "cloud-text-vault.enc.json"
    }
}
