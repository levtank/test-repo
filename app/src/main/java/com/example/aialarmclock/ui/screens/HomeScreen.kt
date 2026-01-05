package com.example.aialarmclock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aialarmclock.data.local.entities.QuestionMode
import com.example.aialarmclock.ui.viewmodels.AlarmViewModel

@Composable
fun HomeScreen(
    viewModel: AlarmViewModel,
    onSetAlarmClick: () -> Unit,
    onConfigureQuestionsClick: () -> Unit
) {
    val alarm by viewModel.alarm.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Alarm icon
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = "Alarm",
            modifier = Modifier.size(80.dp),
            tint = if (alarm?.isEnabled == true) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Alarm time display
        if (alarm != null) {
            val hour = alarm!!.hour
            val minute = alarm!!.minute
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }

            Text(
                text = String.format("%d:%02d %s", displayHour, minute, amPm),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = if (alarm!!.isEnabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enable/disable switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (alarm!!.isEnabled) "Alarm ON" else "Alarm OFF",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = alarm!!.isEnabled,
                    onCheckedChange = { viewModel.toggleAlarm(it) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Question mode info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Question Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (alarm!!.questionMode) {
                            QuestionMode.TEMPLATE -> "Using your custom questions"
                            QuestionMode.AI_GENERATED -> "AI generates questions from theme: ${alarm!!.theme ?: "morning reflection"}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Text(
                text = "No alarm set",
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        OutlinedButton(
            onClick = onSetAlarmClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (alarm != null) "Edit Alarm" else "Set Alarm")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onConfigureQuestionsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.QuestionAnswer, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Configure Questions")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Test button - triggers alarm immediately
        Button(
            onClick = { viewModel.triggerTestAlarm() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Test Alarm Now")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // API key warning
        if (apiKey.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Add your Claude API key in Settings to enable AI-generated questions",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
