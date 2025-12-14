import React, {useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  useColorScheme,
  ScrollView,
  StatusBar,
  Switch,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../App';
import SentinelHeader from '../components/SentinelHeader';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;

const SettingsScreen = ({}: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [notifications, setNotifications] = useState(true);
  const [autoScan, setAutoScan] = useState(true);
  const [cloudSync, setCloudSync] = useState(false);
  const [debugMode, setDebugMode] = useState(false);

  const SettingItem = ({
    title,
    description,
    value,
    onValueChange,
  }: {
    title: string;
    description: string;
    value: boolean;
    onValueChange: (value: boolean) => void;
  }) => (
    <View
      style={[
        styles.settingItem,
        {
          backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff',
          borderColor: isDarkMode ? '#34495e' : '#ecf0f1',
        },
      ]}>
      <View style={styles.settingTextContainer}>
        <Text style={[styles.settingTitle, isDarkMode && styles.textDark]}>
          {title}
        </Text>
        <Text
          style={[styles.settingDescription, isDarkMode && styles.textDark]}>
          {description}
        </Text>
      </View>
      <Switch
        value={value}
        onValueChange={onValueChange}
        trackColor={{false: '#767577', true: '#3498db'}}
        thumbColor={value ? '#2980b9' : '#f4f3f4'}
      />
    </View>
  );

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
          title="Settings"
          subtitle="Configure your preferences"
          isDarkMode={isDarkMode}
        />

        <View style={styles.section}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            General
          </Text>
          <SettingItem
            title="Notifications"
            description="Enable push notifications for security alerts"
            value={notifications}
            onValueChange={setNotifications}
          />
          <SettingItem
            title="Auto Scan"
            description="Automatically scan for threats on startup"
            value={autoScan}
            onValueChange={setAutoScan}
          />
        </View>

        <View style={styles.section}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Sync & Storage
          </Text>
          <SettingItem
            title="Cloud Sync"
            description="Sync settings and data to cloud"
            value={cloudSync}
            onValueChange={setCloudSync}
          />
        </View>

        <View style={styles.section}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Advanced
          </Text>
          <SettingItem
            title="Debug Mode"
            description="Enable detailed logging and diagnostics"
            value={debugMode}
            onValueChange={setDebugMode}
          />
        </View>

        <View style={styles.infoSection}>
          <View
            style={[
              styles.infoBox,
              {
                backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff',
                borderColor: isDarkMode ? '#34495e' : '#ecf0f1',
              },
            ]}>
            <Text style={[styles.infoTitle, isDarkMode && styles.textDark]}>
              About
            </Text>
            <Text style={[styles.infoText, isDarkMode && styles.textDark]}>
              App Version: 1.0.0
            </Text>
            <Text style={[styles.infoText, isDarkMode && styles.textDark]}>
              Build: E7-MAX-FULL-AUTO
            </Text>
            <Text style={[styles.infoText, isDarkMode && styles.textDark]}>
              Package: com.sentinel
            </Text>
          </View>
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
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 12,
  },
  textDark: {
    color: '#ecf0f1',
  },
  settingItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 8,
  },
  settingTextContainer: {
    flex: 1,
    marginRight: 16,
  },
  settingTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#2c3e50',
    marginBottom: 4,
  },
  settingDescription: {
    fontSize: 13,
    color: '#7f8c8d',
  },
  infoSection: {
    marginTop: 16,
  },
  infoBox: {
    padding: 16,
    borderRadius: 8,
    borderWidth: 1,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 12,
  },
  infoText: {
    fontSize: 14,
    color: '#7f8c8d',
    marginBottom: 4,
  },
});

export default SettingsScreen;
