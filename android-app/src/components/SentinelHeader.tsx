import React from 'react';
import {View, Text, StyleSheet} from 'react-native';

interface SentinelHeaderProps {
  title: string;
  subtitle: string;
  isDarkMode: boolean;
}

const SentinelHeader = ({
  title,
  subtitle,
  isDarkMode,
}: SentinelHeaderProps): React.JSX.Element => {
  return (
    <View style={styles.header}>
      <Text style={[styles.title, isDarkMode && styles.titleDark]}>
        {title}
      </Text>
      <Text style={[styles.subtitle, isDarkMode && styles.subtitleDark]}>
        {subtitle}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  header: {
    marginBottom: 24,
    alignItems: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#2c3e50',
    textAlign: 'center',
    marginBottom: 8,
  },
  titleDark: {
    color: '#ecf0f1',
  },
  subtitle: {
    fontSize: 14,
    color: '#7f8c8d',
    textAlign: 'center',
  },
  subtitleDark: {
    color: '#bdc3c7',
  },
});

export default SentinelHeader;
