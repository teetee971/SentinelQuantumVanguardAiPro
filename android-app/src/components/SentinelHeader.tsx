import React from 'react';
import {View, Text, StyleSheet} from 'react-native';

interface SentinelHeaderProps {
  title: string;
  subtitle?: string;
}

const SentinelHeader: React.FC<SentinelHeaderProps> = ({title, subtitle}) => {
  return (
    <View style={styles.header}>
      <View style={styles.headerContent}>
        <Text style={styles.title}>{title}</Text>
        {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
      </View>
      <View style={styles.decorativeLine} />
    </View>
  );
};

const styles = StyleSheet.create({
  header: {
    backgroundColor: '#16213e',
    paddingVertical: 20,
    paddingHorizontal: 20,
    borderBottomWidth: 2,
    borderBottomColor: '#00d9ff',
  },
  headerContent: {
    alignItems: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#00d9ff',
    textAlign: 'center',
    marginBottom: 5,
  },
  subtitle: {
    fontSize: 14,
    color: '#95a5a6',
    textAlign: 'center',
  },
  decorativeLine: {
    height: 3,
    backgroundColor: '#0f3460',
    marginTop: 10,
    borderRadius: 2,
  },
});

export default SentinelHeader;
