import React, {useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Switch,
} from 'react-native';
import SentinelHeader from '../components/SentinelHeader';

const SettingsScreen: React.FC = () => {
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [darkModeEnabled, setDarkModeEnabled] = useState(true);
  const [autoScanEnabled, setAutoScanEnabled] = useState(true);
  const [quantumModeEnabled, setQuantumModeEnabled] = useState(false);

  return (
    <ScrollView style={styles.container}>
      <SentinelHeader title="Settings" />
      
      <View style={styles.content}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>General</Text>
          
          <View style={styles.settingItem}>
            <Text style={styles.settingLabel}>Enable Notifications</Text>
            <Switch
              value={notificationsEnabled}
              onValueChange={setNotificationsEnabled}
              trackColor={{false: '#767577', true: '#00d9ff'}}
              thumbColor={notificationsEnabled ? '#ffffff' : '#f4f3f4'}
            />
          </View>
          
          <View style={styles.settingItem}>
            <Text style={styles.settingLabel}>Dark Mode</Text>
            <Switch
              value={darkModeEnabled}
              onValueChange={setDarkModeEnabled}
              trackColor={{false: '#767577', true: '#00d9ff'}}
              thumbColor={darkModeEnabled ? '#ffffff' : '#f4f3f4'}
            />
          </View>
        </View>
        
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Security</Text>
          
          <View style={styles.settingItem}>
            <Text style={styles.settingLabel}>Automatic Scanning</Text>
            <Switch
              value={autoScanEnabled}
              onValueChange={setAutoScanEnabled}
              trackColor={{false: '#767577', true: '#00d9ff'}}
              thumbColor={autoScanEnabled ? '#ffffff' : '#f4f3f4'}
            />
          </View>
          
          <View style={styles.settingItem}>
            <Text style={styles.settingLabel}>Quantum Protection Mode</Text>
            <Switch
              value={quantumModeEnabled}
              onValueChange={setQuantumModeEnabled}
              trackColor={{false: '#767577', true: '#00d9ff'}}
              thumbColor={quantumModeEnabled ? '#ffffff' : '#f4f3f4'}
            />
          </View>
        </View>
        
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>About</Text>
          <Text style={styles.infoText}>Version: 1.0.0</Text>
          <Text style={styles.infoText}>Build: 2024.12.11</Text>
          <Text style={styles.infoText}>
            Sentinel Quantum Vanguard AI Pro
          </Text>
        </View>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a2e',
  },
  content: {
    padding: 20,
  },
  section: {
    marginBottom: 30,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#00d9ff',
    marginBottom: 15,
  },
  settingItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#16213e',
    padding: 15,
    borderRadius: 10,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#0f3460',
  },
  settingLabel: {
    fontSize: 16,
    color: '#ecf0f1',
  },
  infoText: {
    fontSize: 14,
    color: '#95a5a6',
    marginBottom: 5,
  },
});

export default SettingsScreen;
