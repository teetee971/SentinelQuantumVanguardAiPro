/**
 * PHASE B - SOC Dashboard Screen
 * 
 * Security Operations Center - Module status and events monitoring
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  useColorScheme,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { RootStackParamList } from '../App';
import SentinelHeader from '../components/SentinelHeader';
import { socModule } from '../modules/soc/SOCModule';

type Props = NativeStackScreenProps<RootStackParamList, 'SOC'>;

const SOCScreen = ({ navigation }: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [refreshing, setRefreshing] = useState(false);
  const [overview, setOverview] = useState(socModule.getSystemOverview());
  const [modules, setModules] = useState(socModule.getModuleStatuses());
  const [events, setEvents] = useState(socModule.getEvents(10));
  const [health, setHealth] = useState(socModule.getSystemHealth());

  useEffect(() => {
    // Initialize SOC on mount
    socModule.initialize();
    loadData();
  }, []);

  const loadData = () => {
    setOverview(socModule.getSystemOverview());
    setModules(socModule.getModuleStatuses());
    setEvents(socModule.getEvents(10));
    setHealth(socModule.getSystemHealth());
  };

  const onRefresh = () => {
    setRefreshing(true);
    loadData();
    setTimeout(() => setRefreshing(false), 500);
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return '#10b981';
      case 'IN_DEVELOPMENT':
        return '#f59e0b';
      case 'DISABLED':
        return '#ef4444';
      default:
        return '#7f8c8d';
    }
  };

  const getHealthColor = (status: string) => {
    switch (status) {
      case 'HEALTHY':
        return '#10b981';
      case 'WARNING':
        return '#f59e0b';
      case 'CRITICAL':
        return '#ef4444';
      default:
        return '#7f8c8d';
    }
  };

  const getEventTypeColor = (type: string) => {
    switch (type) {
      case 'INFO':
        return '#3b82f6';
      case 'WARNING':
        return '#f59e0b';
      case 'ALERT':
        return '#f97316';
      case 'CRITICAL':
        return '#ef4444';
      default:
        return '#7f8c8d';
    }
  };

  const formatTimestamp = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleString();
  };

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: isDarkMode ? '#1a1a1a' : '#f5f5f5' },
      ]}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }>
        <SentinelHeader
          title="SOC Dashboard"
          subtitle="Security Operations Center"
          isDarkMode={isDarkMode}
        />

        {/* System Health */}
        <View
          style={[
            styles.healthCard,
            { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
          ]}>
          <View style={styles.healthHeader}>
            <Text style={[styles.healthTitle, isDarkMode && styles.textDark]}>
              System Health
            </Text>
            <View
              style={[
                styles.healthBadge,
                { backgroundColor: `${getHealthColor(health.status)}20` },
              ]}>
              <Text
                style={[
                  styles.healthBadgeText,
                  { color: getHealthColor(health.status) },
                ]}>
                {health.status}
              </Text>
            </View>
          </View>

          <View style={styles.healthScore}>
            <Text style={[styles.scoreValue, { color: getHealthColor(health.status) }]}>
              {health.score}
            </Text>
            <Text style={[styles.scoreLabel, isDarkMode && styles.textDarkMuted]}>
              / 100
            </Text>
          </View>

          {health.issues.length > 0 && (
            <View style={styles.issuesContainer}>
              <Text style={[styles.issuesTitle, isDarkMode && styles.textDark]}>
                Issues:
              </Text>
              {health.issues.map((issue, index) => (
                <Text
                  key={index}
                  style={[styles.issueText, isDarkMode && styles.textDarkMuted]}>
                  • {issue}
                </Text>
              ))}
            </View>
          )}

          {health.recommendations.length > 0 && (
            <View style={styles.recommendationsContainer}>
              <Text style={[styles.recommendationsTitle, isDarkMode && styles.textDark]}>
                Recommendations:
              </Text>
              {health.recommendations.map((rec, index) => (
                <Text
                  key={index}
                  style={[styles.recommendationText, isDarkMode && styles.textDarkMuted]}>
                  • {rec}
                </Text>
              ))}
            </View>
          )}
        </View>

        {/* Overview Stats */}
        <View style={styles.statsGrid}>
          <View
            style={[
              styles.statCard,
              { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
            ]}>
            <Text style={styles.statValue}>{overview.totalModules}</Text>
            <Text style={[styles.statLabel, isDarkMode && styles.textDarkMuted]}>
              Total Modules
            </Text>
          </View>

          <View
            style={[
              styles.statCard,
              { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
            ]}>
            <Text style={[styles.statValue, { color: '#10b981' }]}>
              {overview.activeModules}
            </Text>
            <Text style={[styles.statLabel, isDarkMode && styles.textDarkMuted]}>
              Active
            </Text>
          </View>

          <View
            style={[
              styles.statCard,
              { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
            ]}>
            <Text style={[styles.statValue, { color: '#f59e0b' }]}>
              {overview.inDevelopmentModules}
            </Text>
            <Text style={[styles.statLabel, isDarkMode && styles.textDarkMuted]}>
              In Development
            </Text>
          </View>

          <View
            style={[
              styles.statCard,
              { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
            ]}>
            <Text style={[styles.statValue, { color: '#ef4444' }]}>
              {overview.disabledModules}
            </Text>
            <Text style={[styles.statLabel, isDarkMode && styles.textDarkMuted]}>
              Disabled
            </Text>
          </View>
        </View>

        {/* Modules Status */}
        <View style={styles.modulesContainer}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Module Status
          </Text>

          {modules.map((module, index) => (
            <View
              key={index}
              style={[
                styles.moduleCard,
                { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
              ]}>
              <View style={styles.moduleHeader}>
                <Text style={[styles.moduleName, isDarkMode && styles.textDark]}>
                  {module.name}
                </Text>
                <View
                  style={[
                    styles.moduleBadge,
                    { backgroundColor: `${getStatusColor(module.status)}20` },
                  ]}>
                  <Text
                    style={[
                      styles.moduleBadgeText,
                      { color: getStatusColor(module.status) },
                    ]}>
                    {module.status}
                  </Text>
                </View>
              </View>

              <Text
                style={[
                  styles.moduleDescription,
                  isDarkMode && styles.textDarkMuted,
                ]}>
                {module.description}
              </Text>

              <Text
                style={[styles.featureCount, isDarkMode && styles.textDarkMuted]}>
                {module.features.length} features available
              </Text>
            </View>
          ))}
        </View>

        {/* Recent Events */}
        <View style={styles.eventsContainer}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Recent Events
          </Text>

          {events.length === 0 ? (
            <View
              style={[
                styles.noEventsCard,
                { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
              ]}>
              <Text style={[styles.noEventsText, isDarkMode && styles.textDarkMuted]}>
                No events recorded yet
              </Text>
            </View>
          ) : (
            events.map((event, index) => (
              <View
                key={index}
                style={[
                  styles.eventCard,
                  { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
                ]}>
                <View style={styles.eventHeader}>
                  <View
                    style={[
                      styles.eventTypeBadge,
                      { backgroundColor: `${getEventTypeColor(event.type)}20` },
                    ]}>
                    <Text
                      style={[
                        styles.eventTypeText,
                        { color: getEventTypeColor(event.type) },
                      ]}>
                      {event.type}
                    </Text>
                  </View>
                  <Text
                    style={[
                      styles.eventTime,
                      isDarkMode && styles.textDarkMuted,
                    ]}>
                    {formatTimestamp(event.timestamp)}
                  </Text>
                </View>

                <Text style={[styles.eventTitle, isDarkMode && styles.textDark]}>
                  {event.title}
                </Text>
                <Text
                  style={[
                    styles.eventDescription,
                    isDarkMode && styles.textDarkMuted,
                  ]}>
                  {event.description}
                </Text>
                <Text
                  style={[styles.eventModule, isDarkMode && styles.textDarkMuted]}>
                  Module: {event.module}
                </Text>
              </View>
            ))
          )}
        </View>

        <View style={styles.footer}>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            Phase B - SOC Dashboard
          </Text>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            Real-time module monitoring - No fake data
          </Text>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
  },
  healthCard: {
    borderRadius: 12,
    padding: 20,
    marginVertical: 20,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  healthHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  healthTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#2c3e50',
  },
  healthBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  healthBadgeText: {
    fontSize: 14,
    fontWeight: '600',
  },
  healthScore: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'center',
    marginVertical: 12,
  },
  scoreValue: {
    fontSize: 48,
    fontWeight: 'bold',
  },
  scoreLabel: {
    fontSize: 20,
    color: '#7f8c8d',
    marginLeft: 4,
  },
  issuesContainer: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#ecf0f1',
  },
  issuesTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 4,
  },
  issueText: {
    fontSize: 13,
    color: '#7f8c8d',
    marginBottom: 2,
  },
  recommendationsContainer: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#ecf0f1',
  },
  recommendationsTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 4,
  },
  recommendationText: {
    fontSize: 13,
    color: '#7f8c8d',
    marginBottom: 2,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginBottom: 20,
  },
  statCard: {
    flex: 1,
    minWidth: '45%',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  statValue: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 12,
    color: '#7f8c8d',
  },
  modulesContainer: {
    marginTop: 20,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 16,
  },
  moduleCard: {
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  moduleHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  moduleName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#2c3e50',
    flex: 1,
  },
  moduleBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
  },
  moduleBadgeText: {
    fontSize: 11,
    fontWeight: '600',
  },
  moduleDescription: {
    fontSize: 14,
    color: '#7f8c8d',
    marginBottom: 8,
  },
  featureCount: {
    fontSize: 12,
    color: '#95a5a6',
  },
  eventsContainer: {
    marginTop: 32,
  },
  noEventsCard: {
    borderRadius: 12,
    padding: 20,
    alignItems: 'center',
  },
  noEventsText: {
    fontSize: 14,
    color: '#7f8c8d',
  },
  eventCard: {
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
  },
  eventHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  eventTypeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
  },
  eventTypeText: {
    fontSize: 10,
    fontWeight: '600',
  },
  eventTime: {
    fontSize: 11,
    color: '#95a5a6',
  },
  eventTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#2c3e50',
    marginBottom: 4,
  },
  eventDescription: {
    fontSize: 13,
    color: '#7f8c8d',
    marginBottom: 4,
  },
  eventModule: {
    fontSize: 11,
    color: '#95a5a6',
  },
  footer: {
    marginTop: 40,
    alignItems: 'center',
    gap: 8,
  },
  footerText: {
    fontSize: 12,
    color: '#7f8c8d',
    textAlign: 'center',
  },
  textDark: {
    color: '#ecf0f1',
  },
  textDarkMuted: {
    color: '#95a5a6',
  },
});

export default SOCScreen;
