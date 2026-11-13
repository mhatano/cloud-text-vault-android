package com.example.vault.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.config.ConfigRepository
import com.example.vault.crypto.CryptoService
import com.example.vault.model.AppConfig
import com.example.vault.model.StorageType
import com.example.vault.model.TextEntry
import com.example.vault.model.VaultPayload
import com.example.vault.storage.CloudStorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultUiState(
    val config: AppConfig = AppConfig(),
    val masterPassword: String = "",
    val entries: List<TextEntry> = emptyList(),
    val selectedEntryId: String? = null,
    val titleInput: String = "",
    val contentInput: String = "",
    val isBusy: Boolean = false,
    val statusMessage: String = "準備完了",
    val errorMessage: String? = null,
    val showSettingsDialog: Boolean = false
) {
    val selectedEntry: TextEntry?
        get() = entries.firstOrNull { it.id == selectedEntryId }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val configRepository = ConfigRepository(application)
    private val cryptoService = CryptoService()
    private val localBaseDir = application.filesDir

    private var currentVault: VaultPayload = VaultPayload.empty()

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        val loadedConfig = configRepository.load()
        val provider = CloudStorageProvider.fromConfig(loadedConfig, localBaseDir)
        _uiState.update {
            it.copy(
                config = loadedConfig,
                statusMessage = "準備完了 — 保存先: ${provider.describeTarget()}"
            )
        }
    }

    fun updateMasterPassword(password: String) {
        _uiState.update { it.copy(masterPassword = password) }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(titleInput = title) }
    }

    fun updateContent(content: String) {
        _uiState.update { it.copy(contentInput = content) }
    }

    fun onSharedTextReceived(sharedText: String) {
        if (sharedText.isNotBlank()) {
            _uiState.update {
                it.copy(
                    selectedEntryId = null,
                    titleInput = "",
                    contentInput = sharedText,
                    statusMessage = "他アプリからの共有テキストを受け取りました。「登録」で暗号化保存できます。"
                )
            }
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun switchStorageType(newType: StorageType) {
        val updatedConfig = _uiState.value.config.copy(activeStorageType = newType).normalized()
        configRepository.save(updatedConfig)
        val provider = CloudStorageProvider.fromConfig(updatedConfig, localBaseDir)
        _uiState.update {
            it.copy(
                config = updatedConfig,
                statusMessage = "保存先を切り替えました: ${provider.describeTarget()}"
            )
        }
    }

    fun saveConfig(newConfig: AppConfig) {
        val normalized = newConfig.normalized()
        configRepository.save(normalized)
        val provider = CloudStorageProvider.fromConfig(normalized, localBaseDir)
        _uiState.update {
            it.copy(
                config = normalized,
                showSettingsDialog = false,
                statusMessage = "設定を保存しました: ${provider.describeTarget()}"
            )
        }
    }

    fun selectEntry(entry: TextEntry) {
        _uiState.update {
            it.copy(
                selectedEntryId = entry.id,
                titleInput = entry.title,
                contentInput = entry.content,
                statusMessage = "参照中: ${entry.displayLabel()}"
            )
        }
    }

    fun clearEditor() {
        _uiState.update {
            it.copy(
                selectedEntryId = null,
                titleInput = "",
                contentInput = "",
                statusMessage = "入力欄をクリアしました。"
            )
        }
    }

    fun syncFromCloud() {
        val state = _uiState.value
        val password = state.masterPassword
        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "マスターパスワードを入力してください。") }
            return
        }

        val provider = CloudStorageProvider.fromConfig(state.config, localBaseDir)
        _uiState.update {
            it.copy(
                isBusy = true,
                statusMessage = "${provider.describeTarget()} から暗号化データを読み込み中..."
            )
        }

        viewModelScope.launch {
            try {
                val loadedVault = withContext(Dispatchers.IO) {
                    val encryptedBytes = provider.loadEncryptedData()
                    cryptoService.decryptPayload(encryptedBytes, password.toCharArray())
                }
                currentVault = loadedVault
                val firstEntry = loadedVault.entries.firstOrNull()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        entries = loadedVault.entries,
                        selectedEntryId = firstEntry?.id,
                        titleInput = firstEntry?.title.orEmpty(),
                        contentInput = firstEntry?.content.orEmpty(),
                        statusMessage = "読込完了 (${loadedVault.entries.size} 件) — ${provider.describeTarget()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "読込エラー: ${e.message}",
                        errorMessage = e.message ?: "クラウドからの読み込みに失敗しました。"
                    )
                }
            }
        }
    }

    fun registerEntry() {
        val state = _uiState.value
        if (state.contentInput.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "登録するテキストを入力してください。") }
            return
        }
        val password = state.masterPassword
        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "マスターパスワードを入力してください。") }
            return
        }

        val newEntry = TextEntry.create(state.titleInput, state.contentInput)
        val provider = CloudStorageProvider.fromConfig(state.config, localBaseDir)

        _uiState.update {
            it.copy(
                isBusy = true,
                statusMessage = "AES-256-GCMで暗号化して ${provider.describeTarget()} に保存中..."
            )
        }

        viewModelScope.launch {
            try {
                val updatedVault = withContext(Dispatchers.IO) {
                    val existingBytes = provider.loadEncryptedData()
                    val basePayload = if (existingBytes != null) {
                        cryptoService.decryptPayload(existingBytes, password.toCharArray())
                    } else {
                        currentVault
                    }
                    val merged = basePayload.withAddedEntry(newEntry)
                    val encryptedBytes = cryptoService.encryptPayload(merged, password.toCharArray())
                    provider.saveEncryptedData(encryptedBytes)
                    merged
                }
                currentVault = updatedVault
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        entries = updatedVault.entries,
                        selectedEntryId = newEntry.id,
                        titleInput = newEntry.title,
                        contentInput = newEntry.content,
                        statusMessage = "登録完了: [${newEntry.displayLabel()}]"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "登録エラー: ${e.message}",
                        errorMessage = e.message ?: "暗号化データの登録に失敗しました。"
                    )
                }
            }
        }
    }

    fun deleteSelectedEntry() {
        val state = _uiState.value
        val selected = state.selectedEntry
        if (selected == null) {
            _uiState.update { it.copy(errorMessage = "削除する項目をドロップダウンから選択してください。") }
            return
        }
        val password = state.masterPassword
        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "マスターパスワードを入力してください。") }
            return
        }

        val provider = CloudStorageProvider.fromConfig(state.config, localBaseDir)
        _uiState.update {
            it.copy(
                isBusy = true,
                statusMessage = "削除をクラウドへ反映中..."
            )
        }

        viewModelScope.launch {
            try {
                val updatedVault = withContext(Dispatchers.IO) {
                    val updated = currentVault.withRemovedEntry(selected.id)
                    val encryptedBytes = cryptoService.encryptPayload(updated, password.toCharArray())
                    provider.saveEncryptedData(encryptedBytes)
                    updated
                }
                currentVault = updatedVault
                val nextFirst = updatedVault.entries.firstOrNull()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        entries = updatedVault.entries,
                        selectedEntryId = nextFirst?.id,
                        titleInput = nextFirst?.title.orEmpty(),
                        contentInput = nextFirst?.content.orEmpty(),
                        statusMessage = "削除完了: ${selected.displayLabel()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "削除エラー: ${e.message}",
                        errorMessage = e.message ?: "削除の保存に失敗しました。"
                    )
                }
            }
        }
    }
}
