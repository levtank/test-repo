package com.example.aialarmclock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.aialarmclock.ai.QuestionGenerator
import com.example.aialarmclock.data.local.entities.QuestionMode
import com.example.aialarmclock.ui.viewmodels.AlarmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionConfigScreen(
    viewModel: AlarmViewModel,
    onNavigateBack: () -> Unit
) {
    val alarm by viewModel.alarm.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    var selectedMode by remember(alarm) {
        mutableStateOf(alarm?.questionMode ?: QuestionMode.TEMPLATE)
    }

    var theme by remember(alarm) {
        mutableStateOf(alarm?.theme ?: QuestionGenerator.DEFAULT_THEME)
    }

    val templateQuestions = remember(alarm) {
        mutableStateListOf<String>().apply {
            addAll(alarm?.templateQuestions ?: QuestionGenerator.SAMPLE_TEMPLATES.take(3))
        }
    }

    var newQuestion by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Questions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode selection
            item {
                Text(
                    text = "Question Mode",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == QuestionMode.TEMPLATE) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedMode == QuestionMode.TEMPLATE,
                                onClick = { selectedMode = QuestionMode.TEMPLATE },
                                role = Role.RadioButton
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == QuestionMode.TEMPLATE,
                            onClick = null
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = "Custom Questions",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Use your own predefined questions",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == QuestionMode.AI_GENERATED) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedMode == QuestionMode.AI_GENERATED,
                                onClick = { selectedMode = QuestionMode.AI_GENERATED },
                                role = Role.RadioButton
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == QuestionMode.AI_GENERATED,
                            onClick = null
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = "AI-Generated Questions",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Claude generates varied questions from a theme",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (apiKey.isNullOrBlank()) {
                                Text(
                                    text = "Requires API key (set in Settings)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Mode-specific configuration
            if (selectedMode == QuestionMode.AI_GENERATED) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Theme for Questions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                item {
                    OutlinedTextField(
                        value = theme,
                        onValueChange = { theme = it },
                        label = { Text("Theme") },
                        placeholder = { Text("e.g., morning reflection and gratitude") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (selectedMode == QuestionMode.TEMPLATE) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your Questions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(templateQuestions.toList()) { question ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = question,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = { templateQuestions.remove(question) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newQuestion,
                            onValueChange = { newQuestion = it },
                            label = { Text("Add a question") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (newQuestion.isNotBlank()) {
                                    templateQuestions.add(newQuestion)
                                    newQuestion = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }
            }

            // Save button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val currentAlarm = alarm
                        if (currentAlarm != null) {
                            viewModel.saveAlarm(
                                hour = currentAlarm.hour,
                                minute = currentAlarm.minute,
                                isEnabled = currentAlarm.isEnabled,
                                questionMode = selectedMode,
                                theme = if (selectedMode == QuestionMode.AI_GENERATED) theme else null,
                                templateQuestions = if (selectedMode == QuestionMode.TEMPLATE) {
                                    templateQuestions.toList()
                                } else null
                            )
                        }
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
