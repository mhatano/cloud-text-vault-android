package com.example.vault

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.vault.ui.MainViewModel
import com.example.vault.ui.VaultScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleSendIntent(intent)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val uiState by viewModel.uiState.collectAsState()
                    VaultScreen(
                        uiState = uiState,
                        onMasterPasswordChange = viewModel::updateMasterPassword,
                        onTitleChange = viewModel::updateTitle,
                        onContentChange = viewModel::updateContent,
                        onStorageTypeSelect = viewModel::switchStorageType,
                        onEntrySelect = viewModel::selectEntry,
                        onSyncClick = viewModel::syncFromCloud,
                        onClearClick = viewModel::clearEditor,
                        onRegisterClick = viewModel::registerEntry,
                        onDeleteSelectedClick = viewModel::deleteSelectedEntry,
                        onOpenSettings = viewModel::openSettings,
                        onCloseSettings = viewModel::closeSettings,
                        onSaveSettings = viewModel::saveConfig,
                        onDismissError = viewModel::dismissError
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.onSharedTextReceived(sharedText)
            }
        }
    }
}
