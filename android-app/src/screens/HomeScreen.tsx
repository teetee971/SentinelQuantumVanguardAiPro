import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
} from 'react-native';
import {StackNavigationProp} from '@react-navigation/stack';
import {RootStackParamList} from '../App';
import SentinelButton from '../components/SentinelButton';
import SentinelHeader from '../components/SentinelHeader';

type HomeScreenNavigationProp = StackNavigationProp<RootStackParamList, 'Home'>;

interface HomeScreenProps {
  navigation: HomeScreenNavigationProp;
}

const HomeScreen: React.FC<HomeScreenProps> = ({navigation}) => {
  return (
    <ScrollView style={styles.container}>
      <SentinelHeader title="Welcome to Sentinel AI" />
      
      <View style={styles.content}>
        <Text style={styles.description}>
          Quantum-powered AI security monitoring and threat detection system
        </Text>
        
        <View style={styles.buttonContainer}>
          <SentinelButton
            title="AI Console"
            onPress={() => navigation.navigate('AIConsole')}
            variant="primary"
          />
          
          <SentinelButton
            title="Settings"
            onPress={() => navigation.navigate('Settings')}
            variant="secondary"
          />
        </View>
        
        <View style={styles.statsContainer}>
          <View style={styles.statCard}>
            <Text style={styles.statValue}>98.7%</Text>
            <Text style={styles.statLabel}>System Integrity</Text>
          </View>
          
          <View style={styles.statCard}>
            <Text style={styles.statValue}>24/7</Text>
            <Text style={styles.statLabel}>Active Monitoring</Text>
          </View>
          
          <View style={styles.statCard}>
            <Text style={styles.statValue}>0</Text>
            <Text style={styles.statLabel}>Active Threats</Text>
          </View>
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
  description: {
    fontSize: 16,
    color: '#ecf0f1',
    textAlign: 'center',
    marginBottom: 30,
    lineHeight: 24,
  },
  buttonContainer: {
    marginBottom: 30,
  },
  statsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
  },
  statCard: {
    backgroundColor: '#16213e',
    padding: 20,
    borderRadius: 10,
    width: '30%',
    alignItems: 'center',
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#0f3460',
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#00d9ff',
    marginBottom: 5,
  },
  statLabel: {
    fontSize: 12,
    color: '#95a5a6',
    textAlign: 'center',
  },
});

export default HomeScreen;
