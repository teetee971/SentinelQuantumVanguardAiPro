/**
 * PHASE B - Security Module Screen
 * 
 * Displays mobile security features and monitoring
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  useColorScheme,
  ScrollView,
  TouchableOpacity,
  Alert,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { RootStackParamList } from '../App';
import SentinelHeader from '../components/SentinelHeader';
import { isFeatureEnabled } from '../config/featureFlags';

type Props = NativeStackScreenProps<RootStackParamList, 'Security'>;

const SecurityScreen = ({ navigation }: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';

  const securityFeatures = [
    {
      id: 'behavioral',
      icon: '🧠',
      name: 'Behavioral Analysis',
      description: 'Local analysis of device usage patterns for anomalies',
      enabled: isFeatureEnabled('SECURITY_BEHAVIORAL_ANALYSIS'),
      status: 'READY',
      scope: 'Local only - no cloud data',
    },
    {
      id: 'network',
      icon: '🌐',
      name: 'Network Anomaly Detection',
      description: 'Monitor network statistics for unusual patterns',
      enabled: isFeatureEnabled('SECURITY_NETWORK_ANOMALY_DETECTION'),
      status: 'READY',
      scope: 'Aggregate stats only - no traffic interception',
    },
    {
      id: 'app',
      icon: '📱',
      name: 'App Anomaly Detection',
      description: 'Scan installed apps for suspicious behavior',
      enabled: isFeatureEnabled('SECURITY_APP_ANOMALY_DETECTION'),
      status: 'READY',
      scope: 'Installed apps analysis only',
    },
    {
      id: 'permissions',
      icon: '🔒',
      name: 'Permissions Monitoring',
      description: 'Track sensitive permission grants',
      enabled: isFeatureEnabled('SECURITY_PERMISSIONS_MONITORING'),
      status: 'READY',
      scope: 'Permission tracking only',
    },
  ];

  const limitations = [
    {
      icon: '❌',
      title: 'NO Global Surveillance',
      description: 'This app does NOT monitor global communications or networks',
    },
    {
      icon: '❌',
      title: 'NO Traffic Interception',
      description: 'Cannot intercept network traffic (requires root/VPN)',
    },
    {
      icon: '❌',
      title: 'NO Remote Monitoring',
      description: 'All analysis is local only, no cloud surveillance',
    },
    {
      icon: '✅',
      title: 'Local Analysis Only',
      description: 'All security checks run on your device',
    },
    {
      icon: '✅',
      title: 'Privacy Focused',
      description: 'Your data stays on your device',
    },
    {
      icon: '✅',
      title: 'Transparent',
      description: 'All capabilities clearly documented',
    },
  ];

  const handleFeaturePress = (feature: typeof securityFeatures[0]) => {
    Alert.alert(
      feature.name,
      `Status: ${feature.enabled ? 'Enabled (Framework)' : 'Disabled'}\n\n` +
      `Description: ${feature.description}\n\n` +
      `Scope: ${feature.scope}\n\n` +
      (feature.enabled
        ? 'Framework is ready but requires native module implementation for full functionality.'
        : 'Enable this feature in Settings to use it.'),
      [{ text: 'OK' }]
    );
  };

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: isDarkMode ? '#1a1a1a' : '#f5f5f5' },
      ]}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <SentinelHeader
          title="Mobile Security"
          subtitle="Phase B - Realistic Security Monitoring"
          isDarkMode={isDarkMode}
        />

        <View style={styles.warningBox}>
          <Text style={[styles.warningTitle, isDarkMode && styles.textDark]}>
            ⚠️ Important Disclaimer
          </Text>
          <Text style={[styles.warningText, isDarkMode && styles.textDark]}>
            This is a local security monitoring framework with realistic
            capabilities only.
            {'\n\n'}
            This app does NOT have:
            {'\n'}• Global surveillance capabilities
            {'\n'}• Network traffic interception
            {'\n'}• Remote monitoring features
            {'\n\n'}
            All analysis is performed locally on your device with no external
            data transmission.
          </Text>
        </View>

        <View style={styles.featuresContainer}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Security Features
          </Text>

          {securityFeatures.map((feature, index) => (
            <TouchableOpacity
              key={index}
              style={[
                styles.featureCard,
                { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
              ]}
              onPress={() => handleFeaturePress(feature)}>
              <View style={styles.featureHeader}>
                <Text style={styles.featureIcon}>{feature.icon}</Text>
                <View style={styles.featureInfo}>
                  <Text
                    style={[styles.featureName, isDarkMode && styles.textDark]}>
                    {feature.name}
                  </Text>
                  <Text
                    style={[
                      styles.featureDescription,
                      isDarkMode && styles.textDarkMuted,
                    ]}>
                    {feature.description}
                  </Text>
                  <Text
                    style={[
                      styles.featureScope,
                      isDarkMode && styles.textDarkMuted,
                    ]}>
                    📌 {feature.scope}
                  </Text>
                </View>
              </View>

              <View
                style={[
                  styles.statusBadge,
                  {
                    backgroundColor: feature.enabled
                      ? 'rgba(16, 185, 129, 0.1)'
                      : 'rgba(127, 140, 141, 0.1)',
                  },
                ]}>
                <Text
                  style={[
                    styles.statusText,
                    { color: feature.enabled ? '#10b981' : '#7f8c8d' },
                  ]}>
                  {feature.enabled ? 'Framework Ready' : 'Disabled'}
                </Text>
              </View>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.limitationsContainer}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Capabilities & Limitations
          </Text>

          {limitations.map((item, index) => (
            <View
              key={index}
              style={[
                styles.limitationCard,
                { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
              ]}>
              <Text style={styles.limitationIcon}>{item.icon}</Text>
              <View style={styles.limitationInfo}>
                <Text
                  style={[
                    styles.limitationTitle,
                    isDarkMode && styles.textDark,
                  ]}>
                  {item.title}
                </Text>
                <Text
                  style={[
                    styles.limitationDescription,
                    isDarkMode && styles.textDarkMuted,
                  ]}>
                  {item.description}
                </Text>
              </View>
            </View>
          ))}
        </View>

        <View style={styles.footer}>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            Phase B - Security Module Framework
          </Text>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            Transparency and realistic capabilities only
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
  warningBox: {
    backgroundColor: 'rgba(245, 158, 11, 0.1)',
    borderLeftWidth: 4,
    borderLeftColor: '#f59e0b',
    borderRadius: 8,
    padding: 16,
    marginVertical: 20,
  },
  warningTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 8,
  },
  warningText: {
    fontSize: 14,
    color: '#2c3e50',
    lineHeight: 20,
  },
  featuresContainer: {
    marginTop: 20,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 16,
  },
  featureCard: {
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  featureHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  featureIcon: {
    fontSize: 32,
    marginRight: 12,
  },
  featureInfo: {
    flex: 1,
  },
  featureName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 4,
  },
  featureDescription: {
    fontSize: 14,
    color: '#7f8c8d',
    lineHeight: 18,
    marginBottom: 4,
  },
  featureScope: {
    fontSize: 12,
    color: '#95a5a6',
    fontStyle: 'italic',
  },
  statusBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
  },
  limitationsContainer: {
    marginTop: 32,
  },
  limitationCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
  },
  limitationIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  limitationInfo: {
    flex: 1,
  },
  limitationTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 2,
  },
  limitationDescription: {
    fontSize: 12,
    color: '#7f8c8d',
    lineHeight: 16,
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

export default SecurityScreen;
