/**
 * Incoming Call Alert Screen
 * 
 * Shows when an incoming call is detected
 * Displays: Number, Country, Risk Level, Actions
 */

import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Modal,
  useColorScheme,
  Alert,
} from 'react-native';
import { callDetectionService, IncomingCallEvent } from '../modules/phone/CallDetectionService';
import { callHistoryStorage } from '../modules/phone/CallHistoryStorage';

interface IncomingCallAlertModalProps {
  visible: boolean;
  onClose: () => void;
}

const IncomingCallAlertModal: React.FC<IncomingCallAlertModalProps> = ({ visible, onClose }) => {
  const isDarkMode = useColorScheme() === 'dark';
  const [currentCall, setCurrentCall] = useState<IncomingCallEvent | null>(null);
  
  useEffect(() => {
    // Listen for incoming calls
    const listenerId = 'incoming_call_alert';
    
    callDetectionService.addListener(listenerId, (event) => {
      setCurrentCall(event);
    });
    
    // Start listening
    callDetectionService.startListening();
    
    return () => {
      callDetectionService.removeListener(listenerId);
    };
  }, []);
  
  if (!visible || !currentCall) {
    return null;
  }
  
  const { phoneNumber, identification } = currentCall;
  const riskLevel = identification?.riskScore?.level || 'SAFE';
  const country = identification?.country?.name || 'Inconnu';
  const countryFlag = identification?.country?.flag || '🌍';
  
  const getRiskColor = () => {
    switch (riskLevel) {
      case 'CRITICAL':
      case 'HIGH':
        return '#ef4444';
      case 'MEDIUM':
        return '#f59e0b';
      case 'LOW':
        return '#3b82f6';
      default:
        return '#10b981';
    }
  };
  
  const getRiskLabel = () => {
    switch (riskLevel) {
      case 'CRITICAL':
        return '🚨 CRITIQUE';
      case 'HIGH':
        return '⚠️ RISQUE ÉLEVÉ';
      case 'MEDIUM':
        return '⚡ RISQUE MOYEN';
      case 'LOW':
        return '🔵 RISQUE FAIBLE';
      default:
        return '✅ SÛR';
    }
  };
  
  const handleAllow = async () => {
    await callHistoryStorage.saveCallEvent({
      phoneNumber,
      timestamp: Date.now(),
      type: 'INCOMING',
      duration: 0,
      action: 'ALLOWED',
      country,
      riskLevel,
      notes: 'Autorisé par l\'utilisateur',
    });
    onClose();
  };
  
  const handleBlock = async () => {
    await callDetectionService.blockCall(phoneNumber);
    Alert.alert(
      'Numéro Bloqué',
      `Le numéro ${phoneNumber} a été ajouté à la liste de blocage.`,
      [{ text: 'OK' }]
    );
    onClose();
  };
  
  const handleFlag = async () => {
    await callDetectionService.flagCall(phoneNumber, 'Signalé comme spam');
    Alert.alert(
      'Numéro Signalé',
      `Le numéro ${phoneNumber} a été signalé comme spam.`,
      [{ text: 'OK' }]
    );
    onClose();
  };
  
  return (
    <Modal
      visible={visible}
      transparent={true}
      animationType="slide"
      onRequestClose={onClose}>
      <View style={styles.modalOverlay}>
        <View style={[
          styles.alertCard,
          { backgroundColor: isDarkMode ? '#1a1a1a' : '#ffffff' }
        ]}>
          {/* Header */}
          <View style={styles.header}>
            <Text style={styles.headerIcon}>📞</Text>
            <Text style={[styles.headerText, isDarkMode && styles.textDark]}>
              Appel Entrant
            </Text>
          </View>
          
          {/* Phone Number */}
          <View style={styles.phoneSection}>
            <Text style={[styles.phoneNumber, isDarkMode && styles.textDark]}>
              {phoneNumber}
            </Text>
            <View style={styles.countryBadge}>
              <Text style={styles.countryFlag}>{countryFlag}</Text>
              <Text style={[styles.countryText, isDarkMode && styles.textDark]}>
                {country}
              </Text>
            </View>
          </View>
          
          {/* Risk Level */}
          <View style={[
            styles.riskBadge,
            { backgroundColor: `${getRiskColor()}20`, borderColor: getRiskColor() }
          ]}>
            <Text style={[styles.riskText, { color: getRiskColor() }]}>
              {getRiskLabel()}
            </Text>
          </View>
          
          {/* Risk Details */}
          {identification?.riskScore?.reasons && identification.riskScore.reasons.length > 0 && (
            <View style={styles.reasonsSection}>
              <Text style={[styles.reasonsTitle, isDarkMode && styles.textDark]}>
                Raisons :
              </Text>
              {identification.riskScore.reasons.map((reason: string, index: number) => (
                <Text key={index} style={[styles.reasonText, isDarkMode && styles.textDarkMuted]}>
                  • {reason}
                </Text>
              ))}
            </View>
          )}
          
          {/* Actions */}
          <View style={styles.actionsSection}>
            <TouchableOpacity
              style={[styles.actionButton, styles.allowButton]}
              onPress={handleAllow}>
              <Text style={styles.actionButtonText}>✅ Autoriser</Text>
            </TouchableOpacity>
            
            <TouchableOpacity
              style={[styles.actionButton, styles.blockButton]}
              onPress={handleBlock}>
              <Text style={styles.actionButtonText}>🚫 Bloquer</Text>
            </TouchableOpacity>
            
            <TouchableOpacity
              style={[styles.actionButton, styles.flagButton]}
              onPress={handleFlag}>
              <Text style={styles.actionButtonText}>🚩 Signaler</Text>
            </TouchableOpacity>
          </View>
          
          {/* Close Button */}
          <TouchableOpacity
            style={styles.closeButton}
            onPress={onClose}>
            <Text style={styles.closeButtonText}>Fermer</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  alertCard: {
    width: '100%',
    maxWidth: 400,
    borderRadius: 16,
    padding: 24,
    elevation: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
  },
  headerIcon: {
    fontSize: 32,
    marginRight: 12,
  },
  headerText: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#2c3e50',
  },
  phoneSection: {
    alignItems: 'center',
    marginBottom: 16,
  },
  phoneNumber: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 8,
  },
  countryBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(59, 130, 246, 0.1)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  countryFlag: {
    fontSize: 20,
    marginRight: 6,
  },
  countryText: {
    fontSize: 14,
    color: '#2c3e50',
    fontWeight: '600',
  },
  riskBadge: {
    padding: 12,
    borderRadius: 8,
    borderWidth: 2,
    alignItems: 'center',
    marginBottom: 16,
  },
  riskText: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  reasonsSection: {
    marginBottom: 20,
    padding: 12,
    backgroundColor: 'rgba(127, 140, 141, 0.1)',
    borderRadius: 8,
  },
  reasonsTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 6,
  },
  reasonText: {
    fontSize: 13,
    color: '#7f8c8d',
    marginVertical: 2,
  },
  actionsSection: {
    gap: 12,
    marginBottom: 16,
  },
  actionButton: {
    paddingVertical: 14,
    borderRadius: 8,
    alignItems: 'center',
  },
  allowButton: {
    backgroundColor: '#10b981',
  },
  blockButton: {
    backgroundColor: '#ef4444',
  },
  flagButton: {
    backgroundColor: '#f59e0b',
  },
  actionButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: 'bold',
  },
  closeButton: {
    paddingVertical: 12,
    alignItems: 'center',
  },
  closeButtonText: {
    color: '#7f8c8d',
    fontSize: 14,
    fontWeight: '600',
  },
  textDark: {
    color: '#ecf0f1',
  },
  textDarkMuted: {
    color: '#95a5a6',
  },
});

export default IncomingCallAlertModal;
