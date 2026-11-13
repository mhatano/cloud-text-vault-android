package com.example.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.vault.model.StorageType
import com.example.vault.model.TextEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    uiState: VaultUiState,
    onMasterPasswordChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onStorageTypeSelect: (StorageType) -> Unit,
    onEntrySelect: (TextEntry) -> Unit,
    onSyncClick: () -> Unit,
    onClearClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onDeleteSelectedClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSaveSettings: (com.example.vault.model.AppConfig) -> Unit,
    onDismissError: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showPassword by remember { mutableStateOf(false) }
    var entriesDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Text Vault") },
                actions = {
                    IconButton(
                        onClick = onSyncClick,
                        enabled = !uiState.isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "クラウドから読込"
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        enabled = !uiState.isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "設定"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 1. Storage Provider Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageType.entries.forEach { type ->
                    FilterChip(
                        selected = uiState.config.activeStorageType == type,
                        onClick = { onStorageTypeSelect(type) },
                        enabled = !uiState.isBusy,
                        label = { Text(type.displayName) }
                    )
                }
            }

            // 2. Master Password Row + Cloud Sync button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.masterPassword,
                    onValueChange = onMasterPasswordChange,
                    label = { Text("マスターパスワード (PBKDF2 + AES-256-GCM)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "パスワード表示切替"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = onSyncClick,
                    enabled = !uiState.isBusy
                ) {
                    Text("読込")
                }
            }

            // 3. Saved Entries Dropdown (Timestamp + Title or #SHA256 Hash)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = entriesDropdownExpanded,
                    onExpandedChange = {
                        if (!uiState.isBusy && uiState.entries.isNotEmpty()) {
                            entriesDropdownExpanded = !entriesDropdownExpanded
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    val selectedLabel = uiState.selectedEntry?.displayLabel()
                        ?: if (uiState.entries.isEmpty()) "（登録データなし —「読込」または新規登録）" else "選択してください (${uiState.entries.size}件)"

                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("登録データ一覧 (選択して参照)") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = entriesDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = entriesDropdownExpanded,
                        onDismissRequest = { entriesDropdownExpanded = false }
                    ) {
                        uiState.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.displayLabel()) },
                                onClick = {
                                    entriesDropdownExpanded = false
                                    onEntrySelect(entry)
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (uiState.contentInput.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(uiState.contentInput))
                        }
                    },
                    enabled = uiState.contentInput.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "本文をコピー"
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !uiState.isBusy && uiState.selectedEntry != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "選択項目を削除"
                    )
                }
            }

            // 4. Title Field (Optional - falls back to #SHA256 hash)
            OutlinedTextField(
                value = uiState.titleInput,
                onValueChange = onTitleChange,
                label = { Text("タイトル (任意 — 未入力時は #SHA256ハッシュ表示)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Main Text Area
            OutlinedTextField(
                value = uiState.contentInput,
                onValueChange = onContentChange,
                label = { Text("テキストデータ") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // 6. Clear Button & Register Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onClearClick,
                    enabled = !uiState.isBusy
                ) {
                    Text("クリア")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onRegisterClick,
                    enabled = !uiState.isBusy
                ) {
                    Text("登録 (暗号化して保存)")
                }
            }

            // 7. Status Footer
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    if (uiState.showSettingsDialog) {
        SettingsDialog(
            initialConfig = uiState.config,
            onDismiss = onCloseSettings,
            onSave = onSaveSettings
        )
    }

    val entryToDelete = uiState.selectedEntry
    if (showDeleteConfirm && entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("削除確認") },
            text = { Text("「${entryToDelete.displayLabel()}」を削除してもよろしいですか？") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSelectedClick()
                    }
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("エラー") },
            text = { Text(uiState.errorMessage) },
            confirmButton = {
                Button(onClick = onDismissError) {
                    Text("OK")
                }
            }
        )
    }
}
