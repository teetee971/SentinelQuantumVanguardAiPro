import React, {useState, useEffect, useCallback} from 'react';
import {
  View,
  Text,
  StyleSheet,
  useColorScheme,
  ScrollView,
  StatusBar,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  TextInput,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../App';
import SentinelHeader from '../components/SentinelHeader';

type Props = NativeStackScreenProps<RootStackParamList, 'Logs'>;

interface LogEntry {
  id: string;
  timestamp: string;
  level: 'info' | 'warning' | 'error' | 'debug';
  message: string;
  source?: string;
}

const LogsScreen = ({}: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [filteredLogs, setFilteredLogs] = useState<LogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedLevel, setSelectedLevel] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [autoRefresh, setAutoRefresh] = useState(true);

  const API_BASE_URL = 'http://localhost:3000'; // Configure according to your backend

  const fetchLogs = useCallback(async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/logs`);
      if (response.ok) {
        const data = await response.json();
        setLogs(data);
      } else {
        // Fallback to demo data if API is not available
        setLogs(generateDemoLogs());
      }
    } catch (error) {
      console.log('Failed to fetch logs, using demo data');
      setLogs(generateDemoLogs());
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  const generateDemoLogs = (): LogEntry[] => {
    const now = new Date();
    return [
      {
        id: '1',
        timestamp: new Date(now.getTime() - 60000).toISOString(),
        level: 'info',
        message: 'Security scan completed successfully',
        source: 'SecurityScanner',
      },
      {
        id: '2',
        timestamp: new Date(now.getTime() - 120000).toISOString(),
        level: 'warning',
        message: 'Unusual network activity detected from IP 192.168.1.105',
        source: 'NetworkMonitor',
      },
      {
        id: '3',
        timestamp: new Date(now.getTime() - 180000).toISOString(),
        level: 'info',
        message: 'AI Analyzer started threat analysis',
        source: 'AIAnalyzer',
      },
      {
        id: '4',
        timestamp: new Date(now.getTime() - 240000).toISOString(),
        level: 'error',
        message: 'Failed to connect to remote server: Connection timeout',
        source: 'CloudSync',
      },
      {
        id: '5',
        timestamp: new Date(now.getTime() - 300000).toISOString(),
        level: 'debug',
        message: 'Processing 1,247 events from queue',
        source: 'EventProcessor',
      },
      {
        id: '6',
        timestamp: new Date(now.getTime() - 360000).toISOString(),
        level: 'info',
        message: 'System startup completed in 3.2s',
        source: 'System',
      },
      {
        id: '7',
        timestamp: new Date(now.getTime() - 420000).toISOString(),
        level: 'warning',
        message: 'High CPU usage detected: 87%',
        source: 'SystemMonitor',
      },
      {
        id: '8',
        timestamp: new Date(now.getTime() - 480000).toISOString(),
        level: 'info',
        message: 'User authentication successful',
        source: 'AuthService',
      },
    ];
  };

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(fetchLogs, 5000); // Auto-refresh every 5 seconds
    return () => clearInterval(interval);
  }, [autoRefresh, fetchLogs]);

  useEffect(() => {
    // Apply filters
    let filtered = logs;

    // Filter by level
    if (selectedLevel !== 'all') {
      filtered = filtered.filter(log => log.level === selectedLevel);
    }

    // Filter by search query
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(
        log =>
          log.message.toLowerCase().includes(query) ||
          log.source?.toLowerCase().includes(query)
      );
    }

    setFilteredLogs(filtered);
  }, [logs, selectedLevel, searchQuery]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchLogs();
  };

  const getLevelColor = (level: LogEntry['level']): string => {
    switch (level) {
      case 'error':
        return '#e74c3c';
      case 'warning':
        return '#f39c12';
      case 'debug':
        return '#9b59b6';
      default:
        return '#3498db';
    }
  };

  const getLevelIcon = (level: LogEntry['level']): string => {
    switch (level) {
      case 'error':
        return '✗';
      case 'warning':
        return '⚠';
      case 'debug':
        return '⚙';
      default:
        return 'ℹ';
    }
  };

  const formatTimestamp = (timestamp: string): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    if (diff < 60000) {
      return 'Just now';
    } else if (diff < 3600000) {
      return `${Math.floor(diff / 60000)}m ago`;
    } else if (diff < 86400000) {
      return `${Math.floor(diff / 3600000)}h ago`;
    } else {
      return date.toLocaleString();
    }
  };

  const levels = ['all', 'info', 'warning', 'error', 'debug'];

  if (loading) {
    return (
      <View style={[styles.container, styles.centerContent]}>
        <ActivityIndicator size="large" color="#3498db" />
        <Text style={styles.loadingText}>Loading logs...</Text>
      </View>
    );
  }

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

      <View style={styles.headerContainer}>
        <SentinelHeader
          title="System Logs"
          subtitle="Real-time Activity Monitor"
          isDarkMode={isDarkMode}
        />

        {/* Search Input */}
        <TextInput
          style={[
            styles.searchInput,
            {
              backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff',
              color: isDarkMode ? '#ecf0f1' : '#2c3e50',
              borderColor: isDarkMode ? '#34495e' : '#d0d7de',
            },
          ]}
          placeholder="Search logs..."
          placeholderTextColor={isDarkMode ? '#7f8c8d' : '#95a5a6'}
          value={searchQuery}
          onChangeText={setSearchQuery}
        />

        {/* Level Filters */}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.filtersContainer}>
          {levels.map(level => (
            <TouchableOpacity
              key={level}
              style={[
                styles.filterButton,
                selectedLevel === level && styles.filterButtonActive,
                {
                  borderColor:
                    selectedLevel === level
                      ? '#3498db'
                      : isDarkMode
                      ? '#34495e'
                      : '#d0d7de',
                },
              ]}
              onPress={() => setSelectedLevel(level)}>
              <Text
                style={[
                  styles.filterButtonText,
                  selectedLevel === level && styles.filterButtonTextActive,
                  isDarkMode && styles.textDark,
                ]}>
                {level.toUpperCase()}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {/* Auto-refresh Toggle */}
        <View style={styles.refreshContainer}>
          <TouchableOpacity
            style={[
              styles.autoRefreshButton,
              autoRefresh && styles.autoRefreshButtonActive,
            ]}
            onPress={() => setAutoRefresh(!autoRefresh)}>
            <Text
              style={[
                styles.autoRefreshText,
                autoRefresh && styles.autoRefreshTextActive,
              ]}>
              {autoRefresh ? '⟳ Auto-refresh ON' : '⟳ Auto-refresh OFF'}
            </Text>
          </TouchableOpacity>
          <Text style={[styles.logCount, isDarkMode && styles.textDark]}>
            {filteredLogs.length} logs
          </Text>
        </View>
      </View>

      <ScrollView
        style={styles.logsContainer}
        contentContainerStyle={styles.logsContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }>
        {filteredLogs.map(log => (
          <View
            key={log.id}
            style={[
              styles.logEntry,
              {
                backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff',
                borderLeftColor: getLevelColor(log.level),
              },
            ]}>
            <View style={styles.logHeader}>
              <View
                style={[
                  styles.levelBadge,
                  {backgroundColor: getLevelColor(log.level)},
                ]}>
                <Text style={styles.levelIcon}>{getLevelIcon(log.level)}</Text>
                <Text style={styles.levelText}>{log.level.toUpperCase()}</Text>
              </View>
              <Text style={[styles.timestamp, isDarkMode && styles.textDark]}>
                {formatTimestamp(log.timestamp)}
              </Text>
            </View>
            <Text style={[styles.logMessage, isDarkMode && styles.textDark]}>
              {log.message}
            </Text>
            {log.source && (
              <Text style={styles.logSource}>Source: {log.source}</Text>
            )}
          </View>
        ))}

        {filteredLogs.length === 0 && (
          <View style={styles.emptyState}>
            <Text style={[styles.emptyText, isDarkMode && styles.textDark]}>
              No logs found
            </Text>
          </View>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centerContent: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#7f8c8d',
  },
  headerContainer: {
    padding: 20,
    paddingBottom: 0,
  },
  textDark: {
    color: '#ecf0f1',
  },
  searchInput: {
    marginBottom: 16,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    borderWidth: 1,
    fontSize: 14,
  },
  filtersContainer: {
    marginBottom: 16,
  },
  filterButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 2,
    marginRight: 8,
  },
  filterButtonActive: {
    backgroundColor: '#3498db',
  },
  filterButtonText: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#7f8c8d',
  },
  filterButtonTextActive: {
    color: '#ffffff',
  },
  refreshContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  autoRefreshButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    backgroundColor: '#95a5a6',
  },
  autoRefreshButtonActive: {
    backgroundColor: '#27ae60',
  },
  autoRefreshText: {
    fontSize: 12,
    color: '#ffffff',
    fontWeight: 'bold',
  },
  autoRefreshTextActive: {
    color: '#ffffff',
  },
  logCount: {
    fontSize: 12,
    color: '#7f8c8d',
  },
  logsContainer: {
    flex: 1,
  },
  logsContent: {
    padding: 20,
    paddingTop: 0,
  },
  logEntry: {
    padding: 12,
    borderRadius: 8,
    marginBottom: 12,
    borderLeftWidth: 4,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  levelBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    gap: 4,
  },
  levelIcon: {
    color: '#ffffff',
    fontSize: 10,
  },
  levelText: {
    color: '#ffffff',
    fontSize: 10,
    fontWeight: 'bold',
  },
  timestamp: {
    fontSize: 11,
    color: '#7f8c8d',
  },
  logMessage: {
    fontSize: 13,
    color: '#2c3e50',
    marginBottom: 4,
  },
  logSource: {
    fontSize: 11,
    color: '#95a5a6',
    fontStyle: 'italic',
  },
  emptyState: {
    paddingVertical: 40,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#7f8c8d',
  },
});

export default LogsScreen;
