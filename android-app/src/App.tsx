import React from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import HomeScreen from './screens/HomeScreen';
import SettingsScreen from './screens/SettingsScreen';
import AIConsoleScreen from './screens/AIConsoleScreen';
import AgentsScreen from './screens/AgentsScreen';
import LogsScreen from './screens/LogsScreen';
import PhoneScreen from './screens/PhoneScreen';
import CallHistoryScreen from './screens/CallHistoryScreen';
import SecurityScreen from './screens/SecurityScreen';
import SOCScreen from './screens/SOCScreen';

export type RootStackParamList = {
  Home: undefined;
  Settings: undefined;
  AIConsole: undefined;
  Agents: undefined;
  Logs: undefined;
  Phone: undefined;
  CallHistory: undefined;
  Security: undefined;
  SOC: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

function App(): React.JSX.Element {
  return (
    <NavigationContainer>
      <Stack.Navigator
        initialRouteName="Home"
        screenOptions={{
          headerStyle: {
            backgroundColor: '#2c3e50',
          },
          headerTintColor: '#ecf0f1',
          headerTitleStyle: {
            fontWeight: 'bold',
          },
        }}>
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{title: 'Sentinel Quantum Vanguard'}}
        />
        <Stack.Screen
          name="AIConsole"
          component={AIConsoleScreen}
          options={{title: 'AI Console'}}
        />
        <Stack.Screen
          name="Settings"
          component={SettingsScreen}
          options={{title: 'Settings'}}
        />
        <Stack.Screen
          name="Agents"
          component={AgentsScreen}
          options={{title: 'AI Agents'}}
        />
        <Stack.Screen
          name="Logs"
          component={LogsScreen}
          options={{title: 'System Logs'}}
        />
        <Stack.Screen
          name="Phone"
          component={PhoneScreen}
          options={{title: 'Phone Security'}}
        />
        <Stack.Screen
          name="CallHistory"
          component={CallHistoryScreen}
          options={{title: 'Call History'}}
        />
        <Stack.Screen
          name="Security"
          component={SecurityScreen}
          options={{title: 'Mobile Security'}}
        />
        <Stack.Screen
          name="SOC"
          component={SOCScreen}
          options={{title: 'SOC Dashboard'}}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

export default App;
