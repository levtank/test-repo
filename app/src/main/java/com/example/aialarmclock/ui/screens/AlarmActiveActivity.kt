package com.example.aialarmclock.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aialarmclock.service.AlarmForegroundService
import com.example.aialarmclock.ui.theme.AIAlarmClockTheme
import com.example.aialarmclock.ui.viewmodels.AlarmActiveViewModel
import com.example.aialarmclock.ui.viewmodels.AlarmState
import com.example.aialarmclock.ui.viewmodels.TranscriptEntry

class AlarmActiveActivity : ComponentActivity() {

    private var alarmService: AlarmForegroundService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as AlarmForegroundService.LocalBinder
            alarmService = localBinder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            alarmService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and show over lock screen
        setupWindowFlags()

        // Bind to the alarm service
        Intent(this, AlarmForegroundService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            AIAlarmClockTheme {
                AlarmActiveScreen(
                    onStopAlarmSound = {
                        alarmService?.stopSound()
                    },
                    onDismiss = {
                        dismissAlarm()
                    }
                )
            }
        }
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dismissAlarm() {
        alarmService?.stopAlarm()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    // Prevent back button from dismissing without ending conversation
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - must complete conversation to dismiss
    }
}

@Composable
fun AlarmActiveScreen(
    viewModel: AlarmActiveViewModel = viewModel(),
    onStopAlarmSound: () -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Dismiss when completed
    LaunchedEffect(uiState.state) {
        if (uiState.state == AlarmState.COMPLETED) {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (uiState.state) {
            AlarmState.RINGING -> RingingState(
                onStopAlarm = {
                    onStopAlarmSound()
                    viewModel.onWakeButtonPressed()
                }
            )
            AlarmState.CONNECTING -> ConnectingState()
            AlarmState.CONVERSING -> ConversationState(
                transcript = uiState.transcript,
                currentAiText = uiState.currentAiText,
                isAiSpeaking = uiState.isAiSpeaking,
                isUserSpeaking = uiState.isUserSpeaking,
                onEndConversation = { viewModel.endConversation() }
            )
            AlarmState.ENDING -> EndingState()
            AlarmState.ERROR -> ErrorState(
                errorMessage = uiState.errorMessage ?: "Something went wrong",
                onRetry = { viewModel.retry() },
                onSkip = { viewModel.skipConversation() }
            )
            AlarmState.COMPLETED -> {
                // Will dismiss automatically
                Text("Done!")
            }
        }
    }
}

@Composable
private fun RingingState(onStopAlarm: () -> Unit) {
    val pulseScale by animateFloatAsState(
        targetValue = 1.3f,
        animationSpec = tween(800),
        label = "alarm_pulse"
    )

    val alarmColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.error,
        animationSpec = tween(500),
        label = "alarm_color"
    )

    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = "Alarm ringing",
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale),
            tint = alarmColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "ALARM",
            style = MaterialTheme.typography.displayMedium,
            color = alarmColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStopAlarm,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp)
        ) {
            Text(
                text = "I'm Awake!",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tap to stop alarm and start your morning reflection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectingState() {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Connecting...",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Preparing your morning reflection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConversationState(
    transcript: List<TranscriptEntry>,
    currentAiText: String,
    isAiSpeaking: Boolean,
    isUserSpeaking: Boolean,
    onEndConversation: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(transcript.size, currentAiText) {
        if (transcript.isNotEmpty() || currentAiText.isNotEmpty()) {
            listState.animateScrollToItem(
                index = maxOf(0, transcript.size - 1 + if (currentAiText.isNotEmpty()) 1 else 0)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Speaking indicator
        SpeakingIndicator(
            isAiSpeaking = isAiSpeaking,
            isUserSpeaking = isUserSpeaking
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Transcript
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(transcript) { entry ->
                TranscriptBubble(entry = entry)
            }

            // Show streaming AI text
            if (currentAiText.isNotEmpty()) {
                item {
                    TranscriptBubble(
                        entry = TranscriptEntry(
                            role = "assistant",
                            text = currentAiText
                        ),
                        isStreaming = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // End conversation button
        Button(
            onClick = onEndConversation,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(
                text = "End Conversation",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun SpeakingIndicator(
    isAiSpeaking: Boolean,
    isUserSpeaking: Boolean
) {
    val indicatorScale by animateFloatAsState(
        targetValue = if (isAiSpeaking || isUserSpeaking) 1.1f else 1f,
        animationSpec = tween(300),
        label = "indicator_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when {
            isAiSpeaking -> {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "AI speaking",
                    modifier = Modifier
                        .size(24.dp)
                        .scale(indicatorScale),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI is speaking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            isUserSpeaking -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Listening",
                    modifier = Modifier
                        .size(24.dp)
                        .scale(indicatorScale),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Listening...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Text(
                    text = "Speak naturally - I'm listening",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TranscriptBubble(
    entry: TranscriptEntry,
    isStreaming: Boolean = false
) {
    val isUser = entry.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (isUser) "You" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (isStreaming) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndingState() {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Saving your reflection...",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Try Again")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Skip & Dismiss")
        }
    }
}
