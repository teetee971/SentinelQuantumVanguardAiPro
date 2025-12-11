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
          title="SUPERPACK MAX E7"
          subtitle="Advanced Security & AI Platform"
          isDarkMode={isDarkMode}
        />

        <View style={styles.moduleContainer}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Active Modules
          </Text>
          <View style={styles.moduleList}>
            {[
              '🛡️ Anti-Fraud Protection',
              '🌐 Network Guardian',
              '🔒 Privacy Guardian',
              '🔍 Pegasus Scan',
              '☁️ Cloud Sync',
              '🤖 System Rootkit Detection',
            ].map((module, index) => (
              <View
                key={index}
                style={[
                  styles.moduleItem,
                  {
                    backgroundColor: isDarkMode ? '#2c3e50' : '#ecf0f1',
                  },
                ]}>
                <Text
                  style={[styles.moduleText, isDarkMode && styles.textDark]}>
                  {module}
                </Text>
              </View>
            ))}
          </View>
        </View>

        <View style={styles.buttonContainer}>
          <SentinelButton
            title="Open AI Console"
            onPress={() => navigation.navigate('AIConsole')}
            isDarkMode={isDarkMode}
            variant="primary"
          />
          <SentinelButton
            title="Settings"
            onPress={() => navigation.navigate('Settings')}
            isDarkMode={isDarkMode}
            variant="secondary"
          />
        </View>

        <View style={styles.footer}>
          <Text style={[styles.footerText, isDarkMode && styles.textDark]}>
            Version 1.0.0 - Build E7
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
  },
  moduleText: {
    fontSize: 16,
    color: '#2c3e50',
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
