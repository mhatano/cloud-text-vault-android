package com.example.vault

import com.example.vault.crypto.CryptoException
import com.example.vault.crypto.CryptoService
import com.example.vault.model.AppConfig
import com.example.vault.model.StorageType
import com.example.vault.model.TextEntry
import com.example.vault.model.VaultPayload
import com.example.vault.storage.DropboxStorageProvider
import com.example.vault.storage.GoogleDriveStorageProvider
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

class CryptoAndModelTest {

    @Test
    fun testDisplayLabelWithTitleAndHashFallback() {
        val withTitle = TextEntry.create("Wi-Fiパスワード", "WPA3-Passphrase-1234")
        assertTrue(withTitle.displayLabel().endsWith(" | Wi-Fiパスワード"))

        val withoutTitle = TextEntry.create("   ", "secret-snippet-body")
        val expectedHash = TextEntry.computeSha256Short("secret-snippet-body")
        assertEquals(12, expectedHash.length)
        assertTrue(withoutTitle.displayLabel().endsWith(" | #$expectedHash"))
    }

    @Test
    fun testEncryptDecryptAndWrongPassword() {
        val crypto = CryptoService()
        val password = "SharedMasterPassword!2026".toCharArray()

        val payload = VaultPayload.empty()
            .withAddedEntry(TextEntry.create("PCと共有するメモ", "Androidからも参照・登録可能"))
            .withAddedEntry(TextEntry.create("", "タイトル未入力のメモ"))

        val encryptedBytes = crypto.encryptPayload(payload, password)
        val jsonStr = String(encryptedBytes, StandardCharsets.UTF_8)
        assertFalse(jsonStr.contains("PCと共有するメモ"))
        assertFalse(jsonStr.contains("タイトル未入力のメモ"))

        val decrypted = crypto.decryptPayload(encryptedBytes, password)
        assertEquals(2, decrypted.entries.size)

        var threwCryptoException = false
        try {
            crypto.decryptPayload(encryptedBytes, "WrongPassword".toCharArray())
        } catch (_: CryptoException) {
            threwCryptoException = true
        }
        assertTrue("Expected CryptoException on wrong password", threwCryptoException)
    }

    @Test
    fun testGoogleDriveStorageProviderWithMockWebServer() {
        val storedBytes = AtomicReference<ByteArray?>(null)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/token") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"access_token":"mock-google-token"}""")
                    }
                    path.startsWith("/drive/v3/files") && path.contains("alt=media") -> {
                        val data = storedBytes.get()
                        if (data == null) {
                            MockResponse().setResponseCode(404)
                        } else {
                            MockResponse().setResponseCode(200).setBody(String(data, StandardCharsets.UTF_8))
                        }
                    }
                    path.startsWith("/drive/v3/files") -> {
                        val body = if (storedBytes.get() == null) {
                            """{"files":[]}"""
                        } else {
                            """{"files":[{"id":"drive-file-1","name":"cloud-text-vault.enc.json"}]}"""
                        }
                        MockResponse().setResponseCode(200).setBody(body)
                    }
                    path.startsWith("/upload/drive/v3/files") -> {
                        val reqStr = request.body.readUtf8()
                        if (path.contains("uploadType=multipart")) {
                            val secondPartStart = reqStr.lastIndexOf("\r\n\r\n")
                            val endBoundary = reqStr.lastIndexOf("\r\n--")
                            val extracted = reqStr.substring(secondPartStart + 4, endBoundary)
                            storedBytes.set(extracted.toByteArray(StandardCharsets.UTF_8))
                        } else {
                            storedBytes.set(reqStr.toByteArray(StandardCharsets.UTF_8))
                        }
                        MockResponse().setResponseCode(200).setBody("""{"id":"drive-file-1"}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val config = AppConfig(
                activeStorageType = StorageType.GOOGLE_DRIVE,
                googleClientId = "cid",
                googleClientSecret = "csecret",
                googleRefreshToken = "rtoken"
            )
            val provider = GoogleDriveStorageProvider(
                config = config,
                httpClient = OkHttpClient(),
                filesApiUrl = "$baseUrl/drive/v3/files",
                uploadApiUrl = "$baseUrl/upload/drive/v3/files",
                tokenUrl = "$baseUrl/token"
            )

            assertNull(provider.loadEncryptedData())

            val sample = """{"version":1,"encrypted":"ok"}""".toByteArray(StandardCharsets.UTF_8)
            provider.saveEncryptedData(sample)

            val loaded = provider.loadEncryptedData()
            assertNotNull(loaded)
            assertArrayEquals(sample, loaded)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testDropboxStorageProviderWithMockWebServer() {
        val storedBytes = AtomicReference<ByteArray?>(null)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/download") -> {
                        val data = storedBytes.get()
                        if (data == null) {
                            MockResponse()
                                .setResponseCode(409)
                                .setBody("""{"error_summary":"path/not_found/..."}""")
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(String(data, StandardCharsets.UTF_8))
                        }
                    }
                    path.startsWith("/upload") -> {
                        storedBytes.set(request.body.readByteArray())
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"name":"cloud-text-vault.enc.json"}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val config = AppConfig(
                activeStorageType = StorageType.DROPBOX,
                dropboxAccessToken = "test-dbx-token"
            )
            val provider = DropboxStorageProvider(
                config = config,
                httpClient = OkHttpClient(),
                downloadUrl = "$baseUrl/download",
                uploadUrl = "$baseUrl/upload",
                tokenUrl = "$baseUrl/token"
            )

            assertNull(provider.loadEncryptedData())

            val sample = """{"dropbox":"ok"}""".toByteArray(StandardCharsets.UTF_8)
            provider.saveEncryptedData(sample)

            val loaded = provider.loadEncryptedData()
            assertNotNull(loaded)
            assertArrayEquals(sample, loaded)
        } finally {
            server.shutdown()
        }
    }
}
