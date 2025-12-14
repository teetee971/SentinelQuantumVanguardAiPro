import React, {useState} from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  useColorScheme,
  ScrollView,
  StatusBar,
  TouchableOpacity,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../App';
import SentinelHeader from '../components/SentinelHeader';

type Props = NativeStackScreenProps<RootStackParamList, 'AIConsole'>;

interface LogEntry {
  timestamp: string;
  message: string;
  type: 'info' | 'success' | 'warning' | 'error';
}

const AIConsoleScreen = ({}: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [command, setCommand] = useState('');
  const [logs, setLogs] = useState<LogEntry[]>([
    {
      timestamp: new Date().toLocaleTimeString(),
      message: 'AI Console initialized',
      type: 'success',
    },
    {
      timestamp: new Date().toLocaleTimeString(),
      message: 'Sentinel Quantum Vanguard AI Pro v1.0.0',
      type: 'info',
    },
    {
      timestamp: new Date().toLocaleTimeString(),
      message: 'All systems operational',
      type: 'success',
    },
  ]);

  const handleCommand = () => {
    if (command.trim() === '') return;

    const newLog: LogEntry = {
      timestamp: new Date().toLocaleTimeString(),
      message: `> ${command}`,
      type: 'info',
    };

    const responseLog: LogEntry = {
      timestamp: new Date().toLocaleTimeString(),
      message: processCommand(command),
      type: 'success',
    };

    setLogs([...logs, newLog, responseLog]);
    setCommand('');
  };

  const processCommand = (cmd: string): string => {
    const lowerCmd = cmd.toLowerCase().trim();
    
    if (lowerCmd === 'help') {
      return 'Available commands: status, scan, modules, clear, help';
    } else if (lowerCmd === 'status') {
      return 'All modules running. Security level: MAXIMUM';
    } else if (lowerCmd === 'scan') {
      return 'Security scan initiated. No threats detected.';
    } else if (lowerCmd === 'modules') {
      return 'Active: Anti-Fraud, Network Guardian, Privacy Guardian, Pegasus Scan, Cloud Sync';
    } else if (lowerCmd === 'clear') {
      setTimeout(() => setLogs([]), 100);
      return 'Console cleared';
    } else {
      return `Unknown command: ${cmd}. Type 'help' for available commands.`;
    }
  };

  const clearLogs = () => {
    setLogs([]);
  };

  const getLogColor = (type: LogEntry['type']): string => {
    switch (type) {
      case 'success':
        return '#27ae60';
      case 'warning':
        return '#f39c12';
      case 'error':
        return '#e74c3c';
      default:
        return isDarkMode ? '#ecf0f1' : '#2c3e50';
    }
  };

  return (
    <View
      style={[
        styles.container,
        {backgroundColor: isDarkMode ? '#1a1a1a' : '#f5f5f5'},
      ]}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={isDarkMode ? '#1a1a1a' : '#f5f5f5'}
      />
      <View style={styles.content}>
        <SentinelHeader
          title="AI Console"
          subtitle="Command Interface"
          isDarkMode={isDarkMode}
        />

        <View
          style={[
            styles.consoleContainer,
            {
              backgroundColor: isDarkMode ? '#0d1117' : '#ffffff',
              borderColor: isDarkMode ? '#30363d' : '#d0d7de',
            },
          ]}>
          <View style={styles.consoleHeader}>
            <Text style={[styles.consoleTitle, isDarkMode && styles.textDark]}>
              Console Output
            </Text>
            <TouchableOpacity onPress={clearLogs} style={styles.clearButton}>
              <Text style={styles.clearButtonText}>Clear</Text>
            </TouchableOpacity>
          </View>

          <ScrollView
            style={styles.logContainer}
            contentContainerStyle={styles.logContent}>
            {logs.map((log, index) => (
              <View key={index} style={styles.logEntry}>
                <Text style={[styles.timestamp, isDarkMode && styles.textDark]}>
                  [{log.timestamp}]
                </Text>
                <Text style={[styles.logMessage, {color: getLogColor(log.type)}]}>
                  {log.message}
                </Text>
              </View>
            ))}
          </ScrollView>
        </View>

        <View style={styles.inputContainer}>
          <TextInput
            style={[
              styles.input,
              {
                backgroundColor: isDarkMode ? '#0d1117' : '#ffffff',
                color: isDarkMode ? '#ecf0f1' : '#2c3e50',
                borderColor: isDarkMode ? '#30363d' : '#d0d7de',
              },
            ]}
            placeholder="Enter command..."
            placeholderTextColor={isDarkMode ? '#8b949e' : '#6e7781'}
            value={command}
            onChangeText={setCommand}
            onSubmitEditing={handleCommand}
            returnKeyType="send"
          />
          <TouchableOpacity
            style={[
              styles.sendButton,
              {backgroundColor: command.trim() ? '#3498db' : '#95a5a6'},
            ]}
            onPress={handleCommand}
            disabled={!command.trim()}>
            <Text style={styles.sendButtonText}>Send</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
    padding: 20,
  },
  consoleContainer: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 8,
    marginVertical: 16,
    overflow: 'hidden',
  },
  consoleHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#30363d',
  },
  consoleTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#2c3e50',
  },
  textDark: {
    color: '#ecf0f1',
  },
  clearButton: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    backgroundColor: '#e74c3c',
    borderRadius: 4,
  },
  clearButtonText: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  logContainer: {
    flex: 1,
  },
  logContent: {
    padding: 12,
  },
  logEntry: {
    marginBottom: 8,
  },
  timestamp: {
    fontSize: 11,
    color: '#7f8c8d',
    fontFamily: 'monospace',
  },
  logMessage: {
    fontSize: 13,
    fontFamily: 'monospace',
    marginTop: 2,
  },
  inputContainer: {
    flexDirection: 'row',
    gap: 8,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 14,
    fontFamily: 'monospace',
  },
  sendButton: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sendButtonText: {
    color: '#ffffff',
    fontWeight: 'bold',
    fontSize: 14,
  },
});

export default AIConsoleScreen;
