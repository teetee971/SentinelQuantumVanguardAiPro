import React from 'react';
import {TouchableOpacity, Text, StyleSheet} from 'react-native';

interface SentinelButtonProps {
  title: string;
  onPress: () => void;
  isDarkMode: boolean;
  variant?: 'primary' | 'secondary';
}

const SentinelButton = ({
  title,
  onPress,
  isDarkMode,
  variant = 'primary',
}: SentinelButtonProps): React.JSX.Element => {
  const isPrimary = variant === 'primary';

  return (
    <TouchableOpacity
      style={[
        styles.button,
        {
          backgroundColor: isPrimary
            ? '#3498db'
            : isDarkMode
            ? '#34495e'
            : '#95a5a6',
        },
      ]}
      onPress={onPress}
      activeOpacity={0.8}>
      <Text style={styles.buttonText}>{title}</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: {
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: 'bold',
  },
});

export default SentinelButton;
