package com.example.aialarmclock.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aialarmclock.ui.viewmodels.AlarmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AlarmViewModel) {
    val currentApiKey by viewModel.apiKey.collectAsState()
    val currentOpenAiApiKey by viewModel.openAiApiKey.collectAsState()
    val permissionNeeded by viewModel.permissionNeeded.collectAsState()

    var apiKeyInput by remember(currentApiKey) {
        mutableStateOf(currentApiKey ?: "")
    }
    var openAiKeyInput by remember(currentOpenAiApiKey) {
        mutableStateOf(currentOpenAiApiKey ?: "")
    }
    var showApiKey by remember { mutableStateOf(false) }
    var showOpenAiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Permission warning
            if (permissionNeeded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Alarm Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This app needs permission to schedule exact alarms. Please enable it in system settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.getAlarmSettingsIntent()?.let { intent ->
                                    // Note: In real app, would use LocalContext
                                }
                            }
                        ) {
                            Text("Open Settings")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // API Key section
            Text(
                text = "Claude API Key",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Required for AI-generated questions. Get your API key from console.anthropic.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-ant-...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (showApiKey) "Hide" else "Show"
                        )
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (apiKeyInput.isNotBlank()) {
                        viewModel.saveApiKey(apiKeyInput)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKeyInput.isNotBlank() && apiKeyInput != currentApiKey
            ) {
                Text("Save API Key")
            }

            if (!currentApiKey.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.clearApiKey()
                        apiKeyInput = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear API Key")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // OpenAI API Key section (for Whisper transcription)
            Text(
                text = "OpenAI API Key",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Required for voice transcription (Whisper). Get your API key from platform.openai.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = openAiKeyInput,
                onValueChange = { openAiKeyInput = it },
                label = { Text("OpenAI API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showOpenAiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
                        Icon(
                            imageVector = if (showOpenAiKey) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (showOpenAiKey) "Hide" else "Show"
                        )
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (openAiKeyInput.isNotBlank()) {
                        viewModel.saveOpenAiApiKey(openAiKeyInput)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = openAiKeyInput.isNotBlank() && openAiKeyInput != currentOpenAiApiKey
            ) {
                Text("Save OpenAI Key")
            }

            if (!currentOpenAiApiKey.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.clearOpenAiApiKey()
                        openAiKeyInput = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear OpenAI Key")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status
            val allKeysConfigured = !currentApiKey.isNullOrBlank() && !currentOpenAiApiKey.isNullOrBlank()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (allKeysConfigured) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Claude API status
                    Text(
                        text = if (currentApiKey.isNullOrBlank()) {
                            "Claude: Not configured (will use default questions)"
                        } else {
                            "Claude: Configured (AI questions enabled)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // OpenAI API status
                    Text(
                        text = if (currentOpenAiApiKey.isNullOrBlank()) {
                            "OpenAI: Not configured (transcription unavailable)"
                        } else {
                            "OpenAI: Configured (Whisper transcription enabled)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
