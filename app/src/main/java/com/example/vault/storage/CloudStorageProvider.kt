package com.example.vault.storage

import com.example.vault.model.AppConfig
import com.example.vault.model.StorageType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface CloudStorageProvider {
    val storageType: StorageType
    fun describeTarget(): String
    fun loadEncryptedData(): ByteArray?
    fun saveEncryptedData(encryptedBytes: ByteArray)

    companion object {
        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        fun fromConfig(
            config: AppConfig,
            localBaseDir: File,
            httpClient: OkHttpClient = defaultHttpClient
        ): CloudStorageProvider {
            val normalized = config.normalized()
            return when (normalized.activeStorageType) {
                StorageType.GOOGLE_DRIVE -> GoogleDriveStorageProvider(normalized, httpClient)
                StorageType.DROPBOX -> DropboxStorageProvider(normalized, httpClient)
                StorageType.LOCAL_FILE -> LocalFileStorageProvider(normalized, localBaseDir)
            }
        }
    }
}

/**
 * Google Drive REST API v3 provider (100% compatible with Desktop Java 25 GoogleDriveStorageProvider).
 */
class GoogleDriveStorageProvider(
    private val config: AppConfig,
    private val httpClient: OkHttpClient,
    private val filesApiUrl: String = DEFAULT_FILES_API_URL,
    private val uploadApiUrl: String = DEFAULT_UPLOAD_API_URL,
    private val tokenUrl: String = DEFAULT_TOKEN_URL
) : CloudStorageProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override val storageType: StorageType = StorageType.GOOGLE_DRIVE

    override fun describeTarget(): String {
        val folder = config.googleFolderId.ifBlank { "マイドライブ(root)" }
        return "Google Drive ($folder / ${config.googleFileName})"
    }

    override fun loadEncryptedData(): ByteArray? {
        val accessToken = resolveAccessToken()
        val fileId = findVaultFileId(accessToken) ?: return null
        val encodedFileId = URLEncoder.encode(fileId, StandardCharsets.UTF_8.name())
        val downloadUrl = "$filesApiUrl/$encodedFileId?alt=media"

        val request = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) {
                return null
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                throw StorageException(
                    "Google Driveからのダウンロードに失敗しました (HTTP ${response.code}): ${
                        String(bytes, StandardCharsets.UTF_8)
                    }"
                )
            }
            return if (bytes.isEmpty()) null else bytes
        }
    }

    override fun saveEncryptedData(encryptedBytes: ByteArray) {
        val accessToken = resolveAccessToken()
        val existingFileId = findVaultFileId(accessToken)
        if (existingFileId != null) {
            updateExistingFile(accessToken, existingFileId, encryptedBytes)
        } else {
            createNewFile(accessToken, encryptedBytes)
        }
    }

    private fun findVaultFileId(accessToken: String): String? {
        val escapedName = config.googleFileName.replace("'", "\\'")
        val query = StringBuilder("trashed = false and name = '$escapedName'")
        val folderId = config.googleFolderId
        val useAppDataFolder = folderId.equals("appDataFolder", ignoreCase = true)

        if (folderId.isNotBlank()) {
            query.append(" and '${folderId.replace("'", "\\'")}' in parents")
        }

        val encodedQ = URLEncoder.encode(query.toString(), StandardCharsets.UTF_8.name())
        val urlBuilder = StringBuilder(filesApiUrl)
            .append("?q=").append(encodedQ)
            .append("&fields=files(id,name,modifiedTime)&pageSize=1&orderBy=modifiedTime%20desc")
        if (useAppDataFolder) {
            urlBuilder.append("&spaces=appDataFolder")
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw StorageException(
                    "Google Driveのファイル検索に失敗しました (HTTP ${response.code}): $bodyStr"
                )
            }
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val files = root["files"]?.jsonArray ?: return null
            if (files.isEmpty()) return null
            return files[0].jsonObject["id"]?.jsonPrimitive?.content
        }
    }

    private fun updateExistingFile(accessToken: String, fileId: String, encryptedBytes: ByteArray) {
        val encodedId = URLEncoder.encode(fileId, StandardCharsets.UTF_8.name())
        val url = "$uploadApiUrl/$encodedId?uploadType=media"
        val mediaType = "application/json; charset=UTF-8".toMediaType()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .patch(encryptedBytes.toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw StorageException(
                    "Google Driveのファイル更新に失敗しました (HTTP ${response.code}): $bodyStr"
                )
            }
        }
    }

    private fun createNewFile(accessToken: String, encryptedBytes: ByteArray) {
        val boundary = "===VaultBoundary_${UUID.randomUUID()}"
        val url = "$uploadApiUrl?uploadType=multipart"

        val metadataObj = buildJsonObject {
            put("name", config.googleFileName)
            put("mimeType", "application/json")
            if (config.googleFolderId.isNotBlank()) {
                put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(config.googleFolderId)) })
            }
        }
        val metadataBytes = metadataObj.toString().toByteArray(StandardCharsets.UTF_8)

        val multipartBody = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            write(metadataBytes)
            write("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            write(encryptedBytes)
            write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()

        val mediaType = "multipart/related; boundary=$boundary".toMediaType()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody.toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw StorageException(
                    "Google Driveへの新規ファイル作成に失敗しました (HTTP ${response.code}): $bodyStr"
                )
            }
        }
    }

    private fun resolveAccessToken(): String {
        if (config.googleRefreshToken.isNotBlank() && config.googleClientId.isNotBlank()) {
            return refreshAccessToken()
        }
        if (config.googleAccessToken.isNotBlank()) {
            return config.googleAccessToken
        }
        throw StorageException("Google Drive APIのアクセストークンまたはリフレッシュトークンが設定されていません。右上の設定アイコンから設定してください。")
    }

    private fun refreshAccessToken(): String {
        val formBuilder = FormBody.Builder()
            .add("client_id", config.googleClientId)
            .add("refresh_token", config.googleRefreshToken)
            .add("grant_type", "refresh_token")

        if (config.googleClientSecret.isNotBlank()) {
            formBuilder.add("client_secret", config.googleClientSecret)
        }

        val request = Request.Builder()
            .url(tokenUrl)
            .post(formBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw StorageException("Google OAuth2トークン更新に失敗しました (HTTP ${response.code}): $bodyStr")
            }
            val token = json.parseToJsonElement(bodyStr).jsonObject["access_token"]?.jsonPrimitive?.content.orEmpty()
            if (token.isBlank()) {
                throw StorageException("Google OAuth2レスポンスに access_token が含まれていません。")
            }
            return token
        }
    }

    companion object {
        const val DEFAULT_FILES_API_URL = "https://www.googleapis.com/drive/v3/files"
        const val DEFAULT_UPLOAD_API_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token"
    }
}

/**
 * Dropbox HTTP API v2 provider (100% compatible with Desktop Java 25 DropboxStorageProvider).
 */
class DropboxStorageProvider(
    private val config: AppConfig,
    private val httpClient: OkHttpClient,
    private val downloadUrl: String = DEFAULT_DOWNLOAD_URL,
    private val uploadUrl: String = DEFAULT_UPLOAD_URL,
    private val tokenUrl: String = DEFAULT_TOKEN_URL
) : CloudStorageProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override val storageType: StorageType = StorageType.DROPBOX

    override fun describeTarget(): String = "Dropbox (${config.dropboxFilePath})"

    override fun loadEncryptedData(): ByteArray? {
        val accessToken = resolveAccessToken()
        val apiArg = buildJsonObject {
            put("path", config.dropboxFilePath)
        }.toString()

        val request = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", apiArg)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (response.code == 409) {
                val bodyStr = String(bytes, StandardCharsets.UTF_8)
                if (bodyStr.contains("not_found")) {
                    return null
                }
            }
            if (!response.isSuccessful) {
                throw StorageException(
                    "Dropboxからのダウンロードに失敗しました (HTTP ${response.code}): ${
                        String(bytes, StandardCharsets.UTF_8)
                    }"
                )
            }
            return if (bytes.isEmpty()) null else bytes
        }
    }

    override fun saveEncryptedData(encryptedBytes: ByteArray) {
        val accessToken = resolveAccessToken()
        val apiArg = buildJsonObject {
            put("path", config.dropboxFilePath)
            put("mode", "overwrite")
            put("autorename", false)
            put("mute", true)
        }.toString()

        val mediaType = "application/octet-stream".toMediaType()
        val request = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", apiArg)
            .post(encryptedBytes.toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw StorageException(
                    "Dropboxへのアップロードに失敗しました (HTTP ${response.code}): $bodyStr"
                )
            }
        }
    }

    private fun resolveAccessToken(): String {
        if (config.dropboxRefreshToken.isNotBlank() && config.dropboxAppKey.isNotBlank()) {
            return refreshAccessToken()
        }
        if (config.dropboxAccessToken.isNotBlank()) {
            return config.dropboxAccessToken
        }
        throw StorageException("Dropbox APIのアクセストークンまたはリフレッシュトークンが設定されていません。右上の設定アイコンから設定してください。")
    }

    private fun refreshAccessToken(): String {
        val formBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", config.dropboxRefreshToken)
            .add("client_id", config.dropboxAppKey)

        if (config.dropboxAppSecret.isNotBlank()) {
            formBuilder.add("client_secret", config.dropboxAppSecret)
        }

        val request = Request.Builder()
            .url(tokenUrl)
            .post(formBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw StorageException("Dropbox OAuth2トークン更新に失敗しました (HTTP ${response.code}): $bodyStr")
            }
            val token = json.parseToJsonElement(bodyStr).jsonObject["access_token"]?.jsonPrimitive?.content.orEmpty()
            if (token.isBlank()) {
                throw StorageException("Dropbox OAuth2レスポンスに access_token が含まれていません。")
            }
            return token
        }
    }

    companion object {
        const val DEFAULT_DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download"
        const val DEFAULT_UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload"
        const val DEFAULT_TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
    }
}

/**
 * Local file storage provider on the Android device.
 */
class LocalFileStorageProvider(
    private val config: AppConfig,
    private val baseDir: File
) : CloudStorageProvider {

    override val storageType: StorageType = StorageType.LOCAL_FILE

    private val targetFile: File
        get() = File(baseDir, config.localFileName)

    override fun describeTarget(): String = "端末内ローカル (${targetFile.name})"

    override fun loadEncryptedData(): ByteArray? {
        if (!targetFile.exists()) return null
        val bytes = targetFile.readBytes()
        return if (bytes.isEmpty()) null else bytes
    }

    override fun saveEncryptedData(encryptedBytes: ByteArray) {
        baseDir.mkdirs()
        val tempFile = File(baseDir, "${targetFile.name}.tmp")
        tempFile.writeBytes(encryptedBytes)
        if (!tempFile.renameTo(targetFile)) {
            targetFile.writeBytes(encryptedBytes)
            tempFile.delete()
        }
    }
}
