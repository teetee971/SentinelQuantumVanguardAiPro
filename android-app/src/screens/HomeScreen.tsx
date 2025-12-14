import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  useColorScheme,
  ScrollView,
  StatusBar,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../App';
import SentinelButton from '../components/SentinelButton';
import SentinelHeader from '../components/SentinelHeader';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

const HomeScreen = ({navigation}: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';

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
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <SentinelHeader
          title="Sentinel Quantum Vanguard"
          subtitle="Phase B - Advanced Mobile Security & SOC"
          isDarkMode={isDarkMode}
        />

        <View style={styles.moduleContainer}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Phase B Modules
          </Text>
          <View style={styles.moduleList}>
            {[
              { icon: '📱', name: 'Phone Security Module', status: 'IN_DEVELOPMENT' },
              { icon: '🔒', name: 'Mobile Security Module', status: 'IN_DEVELOPMENT' },
              { icon: '🎯', name: 'SOC Dashboard', status: 'ACTIVE' },
            ].map((module, index) => (
              <View
                key={index}
                style={[
                  styles.moduleItem,
                  {
                    backgroundColor: isDarkMode ? '#2c3e50' : '#ecf0f1',
                  },
                ]}>
                <View style={styles.moduleHeader}>
                  <Text style={styles.moduleIcon}>{module.icon}</Text>
                  <Text
                    style={[styles.moduleText, isDarkMode && styles.textDark]}>
                    {module.name}
                  </Text>
                </View>
                <View style={[
                  styles.statusBadge,
                  { backgroundColor: module.status === 'ACTIVE' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(245, 158, 11, 0.1)' }
                ]}>
                  <Text style={[
                    styles.statusText,
                    { color: module.status === 'ACTIVE' ? '#10b981' : '#f59e0b' }
                  ]}>
                    {module.status}
                  </Text>
                </View>
              </View>
            ))}
          </View>
        </View>

        <View style={styles.infoBox}>
          <Text style={[styles.infoTitle, isDarkMode && styles.textDark]}>
            ℹ️ Phase B Active
          </Text>
          <Text style={[styles.infoText, isDarkMode && styles.textDark]}>
            Advanced mobile security modules with realistic capabilities.
            All features comply with Google Play policies.
            Full transparency - no unrealistic promises.
          </Text>
        </View>

        <View style={styles.buttonContainer}>
          <SentinelButton
            title="📱 Phone Security"
            onPress={() => navigation.navigate('Phone')}
            isDarkMode={isDarkMode}
            variant="primary"
          />
          <SentinelButton
            title="🔒 Mobile Security"
            onPress={() => navigation.navigate('Security')}
            isDarkMode={isDarkMode}
            variant="primary"
          />
          <SentinelButton
            title="🎯 SOC Dashboard"
            onPress={() => navigation.navigate('SOC')}
            isDarkMode={isDarkMode}
            variant="primary"
          />
          <SentinelButton
            title="🤖 AI Agents (Phase A)"
            onPress={() => navigation.navigate('Agents')}
            isDarkMode={isDarkMode}
            variant="secondary"
          />
          <SentinelButton
            title="📊 System Logs"
            onPress={() => navigation.navigate('Logs')}
            isDarkMode={isDarkMode}
            variant="secondary"
          />
          <SentinelButton
            title="⚙️ Settings"
            onPress={() => navigation.navigate('Settings')}
            isDarkMode={isDarkMode}
            variant="secondary"
          />
        </View>

        <View style={styles.footer}>
          <Text style={[styles.footerText, isDarkMode && styles.textDark]}>
            Version 2.0.0 - Phase B
          </Text>
          <Text style={[styles.footerText, isDarkMode && styles.textDark]}>
            © 2024 Sentinel Quantum Vanguard AI Pro
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
  moduleContainer: {
    marginVertical: 20,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 16,
  },
  textDark: {
    color: '#ecf0f1',
  },
  moduleList: {
    gap: 12,
  },
  moduleItem: {
    padding: 16,
    borderRadius: 8,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.1,
    shadowRadius: 4,
    marginBottom: 8,
  },
  moduleHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  moduleIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  moduleText: {
    fontSize: 16,
    color: '#2c3e50',
    flex: 1,
  },
  statusBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 11,
    fontWeight: '600',
  },
  infoBox: {
    backgroundColor: 'rgba(59, 130, 246, 0.1)',
    borderLeftWidth: 4,
    borderLeftColor: '#3b82f6',
    borderRadius: 8,
    padding: 16,
    marginVertical: 20,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#2c3e50',
    lineHeight: 20,
  },
  buttonContainer: {
    marginTop: 24,
    gap: 12,
  },
  footer: {
    marginTop: 40,
    alignItems: 'center',
    gap: 8,
  },
  footerText: {
    fontSize: 12,
    color: '#7f8c8d',
  },
});

export default HomeScreen;
