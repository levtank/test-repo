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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aialarmclock.service.AlarmForegroundService
import com.example.aialarmclock.speech.SpeechRecognitionManager
import com.example.aialarmclock.speech.TextToSpeechManager
import com.example.aialarmclock.ui.theme.AIAlarmClockTheme
import com.example.aialarmclock.ui.viewmodels.AlarmActiveViewModel
import com.example.aialarmclock.ui.viewmodels.AlarmState

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

    // Prevent back button from dismissing without answering
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - must answer to dismiss
    }
}

@Composable
fun AlarmActiveScreen(
    viewModel: AlarmActiveViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Initialize TTS and STT managers
    val ttsManager = remember { TextToSpeechManager(context) }
    val sttManager = remember { SpeechRecognitionManager(context) }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
            sttManager.destroy()
        }
    }

    // Load question on start
    LaunchedEffect(Unit) {
        viewModel.loadQuestion()
    }

    // Handle TTS when question is ready
    LaunchedEffect(uiState.state, uiState.question) {
        if (uiState.state == AlarmState.SPEAKING && uiState.question.isNotEmpty()) {
            val success = ttsManager.speak(uiState.question)
            if (success) {
                viewModel.onSpeakingComplete()
            } else {
                viewModel.onSpeakingError()
            }
        }
    }

    // Handle STT when ready to listen
    LaunchedEffect(uiState.state) {
        if (uiState.state == AlarmState.LISTENING) {
            sttManager.startListening(object : SpeechRecognitionManager.SpeechListener {
                override fun onReadyForSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onEndOfSpeech() {}

                override fun onResult(transcription: String) {
                    viewModel.onSpeechResult(transcription)
                }

                override fun onError(errorCode: Int, errorMessage: String) {
                    viewModel.onSpeechError(errorCode, errorMessage)
                }

                override fun onPartialResult(partialText: String) {
                    viewModel.onPartialResult(partialText)
                }
            })
        }
    }

    // Dismiss when completed
    LaunchedEffect(uiState.state) {
        if (uiState.state == AlarmState.COMPLETED) {
            onDismiss()
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uiState.state) {
                AlarmState.LOADING -> LoadingState()
                AlarmState.SPEAKING -> SpeakingState(question = uiState.question)
                AlarmState.LISTENING -> ListeningState(
                    question = uiState.question,
                    partialResponse = uiState.partialResponse
                )
                AlarmState.PROCESSING -> ProcessingState(
                    response = uiState.transcribedResponse
                )
                AlarmState.ERROR -> ErrorState(
                    errorMessage = uiState.errorMessage ?: "Something went wrong",
                    onRetry = { viewModel.retryListening() },
                    onSkip = { viewModel.skipQuestion() }
                )
                AlarmState.COMPLETED -> {
                    // Will dismiss automatically
                    Text("Done!")
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Preparing your question...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SpeakingState(question: String) {
    val scale by animateFloatAsState(
        targetValue = 1.1f,
        animationSpec = tween(500),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = "Speaking",
            modifier = Modifier
                .size(80.dp)
                .scale(scale),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = question,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Listening...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ListeningState(
    question: String,
    partialResponse: String
) {
    val pulseScale by animateFloatAsState(
        targetValue = 1.2f,
        animationSpec = tween(600),
        label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Listening",
            modifier = Modifier
                .size(100.dp)
                .scale(pulseScale),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Speak your answer...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (partialResponse.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = partialResponse,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ProcessingState(response: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Saving your response...",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "\"$response\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
