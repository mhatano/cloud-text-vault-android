package com.example.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.vault.model.AppConfig
import com.example.vault.model.StorageType

@Composable
fun SettingsDialog(
    initialConfig: AppConfig,
    onDismiss: () -> Unit,
    onSave: (AppConfig) -> Unit
) {
    var selectedTab by remember {
        mutableIntStateOf(
            when (initialConfig.activeStorageType) {
                StorageType.GOOGLE_DRIVE -> 0
                StorageType.DROPBOX -> 1
                StorageType.LOCAL_FILE -> 2
            }
        )
    }

    var googleAccessToken by remember { mutableStateOf(initialConfig.googleAccessToken) }
    var googleClientId by remember { mutableStateOf(initialConfig.googleClientId) }
    var googleClientSecret by remember { mutableStateOf(initialConfig.googleClientSecret) }
    var googleRefreshToken by remember { mutableStateOf(initialConfig.googleRefreshToken) }
    var googleFolderId by remember { mutableStateOf(initialConfig.googleFolderId) }
    var googleFileName by remember { mutableStateOf(initialConfig.googleFileName) }

    var dropboxAccessToken by remember { mutableStateOf(initialConfig.dropboxAccessToken) }
    var dropboxAppKey by remember { mutableStateOf(initialConfig.dropboxAppKey) }
    var dropboxAppSecret by remember { mutableStateOf(initialConfig.dropboxAppSecret) }
    var dropboxRefreshToken by remember { mutableStateOf(initialConfig.dropboxRefreshToken) }
    var dropboxFilePath by remember { mutableStateOf(initialConfig.dropboxFilePath) }

    var localFileName by remember { mutableStateOf(initialConfig.localFileName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "クラウド保存先設定",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Google Drive") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Dropbox") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("ローカル") }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            Text(
                                text = "PC版の設定画面（または ~/.cloud-text-vault/config.json）で取得した Client ID / Secret / Refresh Token または Access Token を設定すると、PC版と同じ暗号化ファイルを共有できます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = googleAccessToken,
                                onValueChange = { googleAccessToken = it },
                                label = { Text("OAuth Access Token (直接指定時)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = googleClientId,
                                onValueChange = { googleClientId = it },
                                label = { Text("OAuth Client ID (Refresh利用時)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = googleClientSecret,
                                onValueChange = { googleClientSecret = it },
                                label = { Text("OAuth Client Secret") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = googleRefreshToken,
                                onValueChange = { googleRefreshToken = it },
                                label = { Text("OAuth Refresh Token") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = googleFolderId,
                                onValueChange = { googleFolderId = it },
                                label = { Text("保存先フォルダID (空欄=root / appDataFolder可)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = googleFileName,
                                onValueChange = { googleFileName = it },
                                label = { Text("保存ファイル名") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        1 -> {
                            Text(
                                text = "Dropbox App Console または PC版で取得した Access Token、もしくは App Key + Refresh Token を設定してください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = dropboxAccessToken,
                                onValueChange = { dropboxAccessToken = it },
                                label = { Text("Dropbox Access Token (直接指定時)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = dropboxAppKey,
                                onValueChange = { dropboxAppKey = it },
                                label = { Text("Dropbox App Key (Refresh利用時)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = dropboxAppSecret,
                                onValueChange = { dropboxAppSecret = it },
                                label = { Text("Dropbox App Secret") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = dropboxRefreshToken,
                                onValueChange = { dropboxRefreshToken = it },
                                label = { Text("Dropbox Refresh Token") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = dropboxFilePath,
                                onValueChange = { dropboxFilePath = it },
                                label = { Text("Dropbox内パス (例: /cloud-text-vault.enc.json)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        else -> {
                            Text(
                                text = "Android端末内のアプリ専用領域に暗号化ファイルを保存します（単体利用・動作検証用）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = localFileName,
                                onValueChange = { localFileName = it },
                                label = { Text("ローカル保存ファイル名") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = {
                            val activeType = when (selectedTab) {
                                0 -> StorageType.GOOGLE_DRIVE
                                1 -> StorageType.DROPBOX
                                else -> StorageType.LOCAL_FILE
                            }
                            onSave(
                                AppConfig(
                                    activeStorageType = activeType,
                                    googleAccessToken = googleAccessToken,
                                    googleClientId = googleClientId,
                                    googleClientSecret = googleClientSecret,
                                    googleRefreshToken = googleRefreshToken,
                                    googleFolderId = googleFolderId,
                                    googleFileName = googleFileName,
                                    dropboxAccessToken = dropboxAccessToken,
                                    dropboxAppKey = dropboxAppKey,
                                    dropboxAppSecret = dropboxAppSecret,
                                    dropboxRefreshToken = dropboxRefreshToken,
                                    dropboxFilePath = dropboxFilePath,
                                    localFileName = localFileName
                                )
                            )
                        }
                    ) {
                        Text("設定を保存")
                    }
                }
            }
        }
    }
}
