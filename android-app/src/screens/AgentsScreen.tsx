import React, {useState, useEffect} from 'react';
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
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../App';
import SentinelHeader from '../components/SentinelHeader';
import {API_BASE_URL, DEMO_AGENTS} from '../config/api';

type Props = NativeStackScreenProps<RootStackParamList, 'Agents'>;

interface Agent {
  id: string;
  name: string;
  status: 'idle' | 'running' | 'completed' | 'error';
  description: string;
  lastRun?: string;
}

const AgentsScreen = ({}: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [executing, setExecuting] = useState<string | null>(null);

  const fetchAgents = async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/agents`);
      if (response.ok) {
        const data = await response.json();
        setAgents(data);
      } else {
        // Fallback to demo data if API is not available
        setAgents(DEMO_AGENTS);
      }
    } catch (error) {
      console.log('Failed to fetch agents, using demo data');
      setAgents(DEMO_AGENTS);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchAgents();
    // Auto-refresh every 10 seconds
    const interval = setInterval(fetchAgents, 10000);
    return () => clearInterval(interval);
  }, []);

  const onRefresh = () => {
    setRefreshing(true);
    fetchAgents();
  };

  const executeAgent = async (agentId: string) => {
    setExecuting(agentId);
    try {
      const response = await fetch(`${API_BASE_URL}/api/agents/${agentId}/execute`, {
        method: 'POST',
      });
      if (response.ok) {
        // Update agent status
        setAgents(prevAgents =>
          prevAgents.map(agent =>
            agent.id === agentId ? {...agent, status: 'running'} : agent
          )
        );
      }
    } catch (error) {
      console.log('Failed to execute agent');
    } finally {
      setExecuting(null);
    }
  };

  const getStatusColor = (status: Agent['status']): string => {
    switch (status) {
      case 'running':
        return '#3498db';
      case 'completed':
        return '#27ae60';
      case 'error':
        return '#e74c3c';
      default:
        return '#95a5a6';
    }
  };

  const getStatusIcon = (status: Agent['status']): string => {
    switch (status) {
      case 'running':
        return '⚡';
      case 'completed':
        return '✓';
      case 'error':
        return '✗';
      default:
        return '○';
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.centerContent]}>
        <ActivityIndicator size="large" color="#3498db" />
        <Text style={styles.loadingText}>Loading agents...</Text>
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
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }>
        <SentinelHeader
          title="AI Agents"
          subtitle="Manage and Execute Agents"
          isDarkMode={isDarkMode}
        />

        <View style={styles.agentsContainer}>
          {agents.map(agent => (
            <View
              key={agent.id}
              style={[
                styles.agentCard,
                {
                  backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff',
                  borderColor: isDarkMode ? '#34495e' : '#ecf0f1',
                },
              ]}>
              <View style={styles.agentHeader}>
                <View style={styles.agentTitleContainer}>
                  <Text
                    style={[styles.agentName, isDarkMode && styles.textDark]}>
                    {agent.name}
                  </Text>
                  <View
                    style={[
                      styles.statusBadge,
                      {backgroundColor: getStatusColor(agent.status)},
                    ]}>
                    <Text style={styles.statusIcon}>
                      {getStatusIcon(agent.status)}
                    </Text>
                    <Text style={styles.statusText}>
                      {agent.status.toUpperCase()}
                    </Text>
                  </View>
                </View>
              </View>

              <Text
                style={[
                  styles.agentDescription,
                  isDarkMode && styles.textDark,
                ]}>
                {agent.description}
              </Text>

              {agent.lastRun && (
                <Text style={styles.lastRun}>Last run: {agent.lastRun}</Text>
              )}

              <TouchableOpacity
                style={[
                  styles.executeButton,
                  agent.status === 'running' && styles.executeButtonDisabled,
                  executing === agent.id && styles.executeButtonExecuting,
                ]}
                onPress={() => executeAgent(agent.id)}
                disabled={agent.status === 'running' || executing === agent.id}>
                {executing === agent.id ? (
                  <ActivityIndicator size="small" color="#ffffff" />
                ) : (
                  <Text style={styles.executeButtonText}>
                    {agent.status === 'running' ? 'Running...' : 'Execute'}
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          ))}
        </View>
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
  scrollContent: {
    padding: 20,
  },
  textDark: {
    color: '#ecf0f1',
  },
  agentsContainer: {
    gap: 16,
  },
  agentCard: {
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  agentHeader: {
    marginBottom: 12,
  },
  agentTitleContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 8,
  },
  agentName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#2c3e50',
    flex: 1,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
    gap: 4,
  },
  statusIcon: {
    color: '#ffffff',
    fontSize: 12,
  },
  statusText: {
    color: '#ffffff',
    fontSize: 11,
    fontWeight: 'bold',
  },
  agentDescription: {
    fontSize: 14,
    color: '#7f8c8d',
    marginBottom: 8,
  },
  lastRun: {
    fontSize: 12,
    color: '#95a5a6',
    fontStyle: 'italic',
    marginBottom: 12,
  },
  executeButton: {
    backgroundColor: '#3498db',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
  },
  executeButtonDisabled: {
    backgroundColor: '#95a5a6',
  },
  executeButtonExecuting: {
    backgroundColor: '#2980b9',
  },
  executeButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: 'bold',
  },
});

export default AgentsScreen;
