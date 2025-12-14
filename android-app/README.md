# Sentinel Quantum Vanguard AI Pro - Android App

React Native Android application with TypeScript for the Sentinel Quantum Vanguard AI Pro SUPERPACK MAX E7.

## Features

- **Home Screen**: Overview of all active security modules
- **AI Console**: Interactive command-line interface for AI operations
- **AI Agents Screen**: Manage and execute AI agents with real-time status
- **System Logs Screen**: Real-time log viewer with filtering and auto-refresh
- **Settings Screen**: Configure application preferences

### Active Security Modules

- 🛡️ Anti-Fraud Protection
- 🌐 Network Guardian
- 🔒 Privacy Guardian
- 🔍 Pegasus Scan
- ☁️ Cloud Sync
- 🤖 System Rootkit Detection

## API Integration

The app integrates with backend APIs:
- **GET /api/agents** - Fetch list of AI agents
- **POST /api/agents/:id/execute** - Execute a specific agent
- **GET /api/logs** - Fetch system logs

Note: The app includes demo data fallback if backend APIs are unavailable.

## Prerequisites

- Node.js >= 18
- React Native development environment
- Android Studio
- JDK 17 or newer
- Android SDK (API 34)

## Installation

1. Install dependencies:
```bash
npm install
```

2. For Android:
```bash
npm run android
```

## Running the App

### Start Metro Bundler
```bash
npm start
```

### Run on Android
```bash
npm run android
```

## Building for Production

### Android APK
```bash
npm run build
```

Or manually:
```bash
cd android
./gradlew assembleRelease
```

The APK will be available at:
`android/app/build/outputs/apk/release/app-release.apk`

## Project Structure

```
android-app/
├── src/
│   ├── App.tsx                 # Main navigation component
│   ├── screens/
│   │   ├── HomeScreen.tsx      # Home screen with module overview
│   │   ├── AIConsoleScreen.tsx # AI command console
│   │   ├── AgentsScreen.tsx    # AI agents management
│   │   ├── LogsScreen.tsx      # System logs viewer
│   │   └── SettingsScreen.tsx  # Application settings
│   └── components/
│       ├── SentinelButton.tsx  # Custom button component
│       └── SentinelHeader.tsx  # Custom header component
├── android/                    # Native Android code
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/sentinel/
│   │   │   │   ├── MainActivity.java
│   │   │   │   └── MainApplication.java
│   │   │   ├── res/            # Android resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   ├── build.gradle
│   └── settings.gradle
├── index.js                    # Entry point
├── package.json                # Dependencies
├── tsconfig.json               # TypeScript configuration
└── README.md                   # This file
```

## Available Commands

### AI Console Commands

- `help` - Show available commands
- `status` - Display system status
- `scan` - Run security scan
- `modules` - List active modules
- `clear` - Clear console output

## Tech Stack

- React Native 0.73.x
- TypeScript 5.x
- React Navigation 6.x
- Android Gradle Plugin 8.1.4
- Gradle 8.3

## CI/CD

GitHub Actions workflow for automated APK builds is configured in `.github/workflows/android-apk.yml`

## License

See LICENSE file in the root directory.

## Version

- App Version: 1.0.0
- Build: E7-MAX-FULL-AUTO
- Package: com.sentinel
