# Sentinel Quantum Vanguard AI - Android App

React Native mobile application for the Sentinel Quantum Vanguard AI security monitoring system.

## Features

- **Home Screen**: Dashboard with system status and quick access to key features
- **AI Console**: Interactive AI chat interface for security queries and commands
- **Settings Screen**: Configure app preferences and security settings

## Prerequisites

- Node.js >= 16
- React Native development environment
- Android Studio and Android SDK
- Java Development Kit (JDK) 11 or newer

## Installation

1. Install dependencies:
```bash
npm install
```

2. Install iOS dependencies (macOS only):
```bash
cd ios && pod install && cd ..
```

## Running the App

### Android

```bash
npm run android
```

Or use Android Studio:
1. Open the `android` folder in Android Studio
2. Run the app on an emulator or connected device

### iOS (macOS only)

```bash
npm run ios
```

## Development

- `npm start` - Start Metro bundler
- `npm run lint` - Run ESLint
- `npm test` - Run tests

## Project Structure

```
android-app/
├── android/              # Android native code
├── src/
│   ├── App.tsx          # Main app component with navigation
│   ├── screens/         # Screen components
│   │   ├── HomeScreen.tsx
│   │   ├── SettingsScreen.tsx
│   │   └── AIConsoleScreen.tsx
│   └── components/      # Reusable components
│       ├── SentinelButton.tsx
│       └── SentinelHeader.tsx
├── package.json
└── README.md
```

## Architecture

The app uses:
- **React Native 0.72** for cross-platform mobile development
- **React Navigation** for screen navigation
- **TypeScript** for type safety
- **React Hooks** for state management

## Components

### SentinelButton
Customizable button component with three variants:
- `primary` - Main action button (cyan)
- `secondary` - Secondary actions (dark blue)
- `danger` - Destructive actions (red)

### SentinelHeader
Consistent header component with title and optional subtitle

## Screens

### HomeScreen
Main dashboard showing:
- System integrity status
- Active monitoring indicator
- Threat count
- Quick access to AI Console and Settings

### AIConsoleScreen
Interactive AI chat interface for:
- Security queries
- System commands
- Real-time threat analysis

### SettingsScreen
Configuration options for:
- Notifications
- Dark mode
- Automatic scanning
- Quantum protection mode

## License

MIT
