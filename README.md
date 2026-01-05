# AI Alarm Clock

An Android alarm clock app that uses Claude AI to ask wake-up questions via voice. When the alarm fires, it speaks a question, listens for your verbal response, transcribes it, and saves it with the date and time. Fully hands-free operation over the lock screen.

## Features

- Single alarm with hands-free operation over lock screen
- Two question modes:
  - **Custom Templates**: Define your own wake-up questions
  - **AI-Generated**: Claude generates varied questions based on a theme
- Text-to-speech reads the question aloud
- Speech recognition transcribes your verbal response
- Response history with timestamps
- Works when phone is locked (no need to unlock)

## Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- A Claude API key from [console.anthropic.com](https://console.anthropic.com)

### Build & Run

1. Open the project in Android Studio
2. Sync Gradle files
3. Run on a physical device (recommended for testing lock screen behavior)

### Configure API Key

1. Open the app
2. Go to Settings tab
3. Enter your Claude API key
4. The key is stored securely on device

## Usage

1. **Set Alarm**: Tap "Set Alarm" on the home screen and pick a time
2. **Configure Questions**: Choose between custom templates or AI-generated questions
3. **Enable**: Toggle the alarm on
4. **Wait**: When the alarm fires, it will:
   - Show a full-screen UI over the lock screen
   - Speak the question using text-to-speech
   - Listen for your verbal response
   - Save your transcribed answer
   - Dismiss the alarm

## Project Structure

```
app/src/main/java/com/example/aialarmclock/
├── AlarmClockApp.kt              # Application class
├── MainActivity.kt               # Main UI with navigation
├── data/                         # Database and preferences
├── service/                      # Foreground service and receivers
├── ai/                           # Claude API client
├── speech/                       # TTS and STT managers
├── alarm/                        # Alarm scheduling
└── ui/                           # Compose screens and ViewModels
```

## Permissions

The app requires these permissions:
- `SCHEDULE_EXACT_ALARM` - Fire alarm at precise time
- `RECORD_AUDIO` - Microphone for speech recognition
- `INTERNET` - Call Claude API
- `WAKE_LOCK` - Keep CPU awake during alarm
- `POST_NOTIFICATIONS` - Show alarm notification

## Tech Stack

- Kotlin
- Jetpack Compose
- Room Database
- DataStore Preferences
- OkHttp + Kotlinx Serialization
- Android TTS and SpeechRecognizer APIs
