package com.example.aialarmclock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aialarmclock.data.local.entities.QuestionMode
import com.example.aialarmclock.ui.viewmodels.AlarmViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetAlarmScreen(
    viewModel: AlarmViewModel,
    onNavigateBack: () -> Unit
) {
    val alarm by viewModel.alarm.collectAsState()

    val calendar = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = alarm?.hour ?: calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = alarm?.minute ?: 0,
        is24Hour = false
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Alarm Time") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TimePicker(state = timePickerState)

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        viewModel.saveAlarm(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                            isEnabled = true,
                            questionMode = alarm?.questionMode ?: QuestionMode.TEMPLATE,
                            theme = alarm?.theme,
                            templateQuestions = alarm?.templateQuestions
                        )
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
