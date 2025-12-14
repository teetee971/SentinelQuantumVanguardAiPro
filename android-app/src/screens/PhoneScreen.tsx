/**
 * PHASE B - Phone Module Screen
 * 
 * Displays phone security features and their status
 */

import React, { useState, useEffect } from 'react';
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
import { phoneModule } from '../modules/phone/PhoneModule';

type Props = NativeStackScreenProps<RootStackParamList, 'Phone'>;

const PhoneScreen = ({ navigation }: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [callLogCount, setCallLogCount] = useState(0);
  const [contactsCount, setContactsCount] = useState(0);
  const [smsCount, setSmsCount] = useState(0);

  const features = [
    {
      id: 'contacts',
      icon: '👥',
      name: 'Contacts Access',
      description: 'Access contacts for caller ID enrichment',
      enabled: isFeatureEnabled('PHONE_CONTACTS_ACCESS'),
      permission: 'READ_CONTACTS',
      status: 'PERMISSION_REQUIRED',
    },
    {
      id: 'call_log',
      icon: '📞',
      name: 'Call Log Analysis',
      description: 'Analyze call history for spam/scam detection',
      enabled: isFeatureEnabled('PHONE_CALL_LOG_ACCESS'),
      permission: 'READ_CALL_LOG',
      status: 'PERMISSION_REQUIRED',
    },
    {
      id: 'sms',
      icon: '💬',
      name: 'SMS Reading (Read Only)',
      description: 'Read SMS for phishing detection',
      enabled: isFeatureEnabled('PHONE_SMS_READ_ACCESS'),
      permission: 'READ_SMS',
      status: 'PERMISSION_REQUIRED',
    },
    {
      id: 'call_recording',
      icon: '🎙️',
      name: 'Call Recording',
      description: 'Record calls (region-dependent, requires consent)',
      enabled: isFeatureEnabled('PHONE_CALL_RECORDING'),
      status: 'NATIVE_MODULE_REQUIRED',
    },
    {
      id: 'ai_analysis',
      icon: '🤖',
      name: 'AI Call Analysis',
      description: 'Local AI analysis for spam/scam detection',
      enabled: isFeatureEnabled('PHONE_AI_CALL_ANALYSIS'),
      status: 'READY',
    },
    {
      id: 'smart_handling',
      icon: '⚡',
      name: 'Smart Call Handling',
      description: 'Intelligent call blocking and filtering',
      enabled: isFeatureEnabled('PHONE_SMART_CALL_HANDLING'),
      status: 'NATIVE_MODULE_REQUIRED',
    },
    {
      id: 'caller_id',
      icon: '🔍',
      name: 'Caller ID Enrichment',
      description: 'Enhanced caller information lookup',
      enabled: isFeatureEnabled('PHONE_CALLER_ID_ENRICHMENT'),
      status: 'READY',
    },
    {
      id: 'country',
      icon: '🌍',
      name: 'Real Country Detection',
      description: 'Detect actual call origin country',
      enabled: isFeatureEnabled('PHONE_COUNTRY_DETECTION'),
      status: 'READY',
    },
    {
      id: 'robocall',
      icon: '🚫',
      name: 'Robocall Detection',
      description: 'Identify automated and spam calls',
      enabled: isFeatureEnabled('PHONE_ROBOCALL_DETECTION'),
      status: 'READY',
    },
  ];

  const handleFeaturePress = (feature: typeof features[0]) => {
    if (!feature.enabled) {
      Alert.alert(
        'Feature Disabled',
        `${feature.name} is currently disabled.\n\n` +
        `Status: ${feature.status}\n` +
        (feature.permission ? `Required permission: ${feature.permission}\n\n` : '') +
        'Enable this feature in Settings to use it.',
        [{ text: 'OK' }]
      );
      return;
    }

    Alert.alert(
      feature.name,
      `This feature is in development.\n\n` +
      `Framework is ready but requires native module implementation.`,
      [{ text: 'OK' }]
    );
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'READY':
        return '#10b981';
      case 'PERMISSION_REQUIRED':
        return '#f59e0b';
      case 'NATIVE_MODULE_REQUIRED':
        return '#ef4444';
      default:
        return '#7f8c8d';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'READY':
        return 'Ready';
      case 'PERMISSION_REQUIRED':
        return 'Needs Permission';
      case 'NATIVE_MODULE_REQUIRED':
        return 'Needs Native Module';
      default:
        return 'Unknown';
    }
  };

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: isDarkMode ? '#1a1a1a' : '#f5f5f5' },
      ]}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <SentinelHeader
          title="Phone Security"
          subtitle="Phase B - Advanced Phone Protection"
          isDarkMode={isDarkMode}
        />

        <View style={styles.infoBox}>
          <Text style={[styles.infoTitle, isDarkMode && styles.textDark]}>
            ⚠️ Important Information
          </Text>
          <Text style={[styles.infoText, isDarkMode && styles.textDark]}>
            All phone features are currently in development phase.
            {'\n\n'}
            Features marked as "Ready" have framework code but require native
            module implementation.
            {'\n\n'}
            Features marked as "Needs Permission" require Android runtime
            permissions that must be granted by the user.
            {'\n\n'}
            NO data is sent to external servers. All processing is local.
          </Text>
        </View>

        <View style={styles.featuresContainer}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Available Features
          </Text>

          {features.map((feature, index) => (
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
                </View>
              </View>

              <View style={styles.featureFooter}>
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
                    {feature.enabled ? 'Enabled' : 'Disabled'}
                  </Text>
                </View>

                <View
                  style={[
                    styles.statusBadge,
                    {
                      backgroundColor: `${getStatusColor(feature.status)}20`,
                    },
                  ]}>
                  <Text
                    style={[
                      styles.statusText,
                      { color: getStatusColor(feature.status) },
                    ]}>
                    {getStatusLabel(feature.status)}
                  </Text>
                </View>
              </View>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.footer}>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            Phase B - Phone Module Framework
          </Text>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            All features comply with Google Play policies
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
  infoBox: {
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
    borderLeftWidth: 4,
    borderLeftColor: '#10b981',
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
  },
  featureFooter: {
    flexDirection: 'row',
    gap: 8,
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
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

export default PhoneScreen;
