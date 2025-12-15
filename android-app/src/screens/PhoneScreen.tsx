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
  Switch,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { RootStackParamList } from '../App';
import SentinelHeader from '../components/SentinelHeader';
import { isFeatureEnabled } from '../config/featureFlags';
import { phoneModule } from '../modules/phone/PhoneModule';
import { callIdentificationService } from '../modules/phone/CallIdentification';
import { aiCallAssistant, zeroInteractionManager, institutionModeManager } from '../modules/phone/AIAssistant';
import IncomingCallAlert from '../components/IncomingCallAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'Phone'>;

const PhoneScreen = ({ navigation }: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [callLogCount, setCallLogCount] = useState(0);
  const [contactsCount, setContactsCount] = useState(0);
  const [smsCount, setSmsCount] = useState(0);
  const [showCallDemo, setShowCallDemo] = useState(false);
  const [demoCallInfo, setDemoCallInfo] = useState<any>(null);
  const [zeroInteractionEnabled, setZeroInteractionEnabled] = useState(false);
  const [institutionModeEnabled, setInstitutionModeEnabled] = useState(false);
  const [aiAssistantEnabled, setAIAssistantEnabled] = useState(false);

  const features = [
    {
      id: 'call_identification',
      icon: '🔍',
      name: 'Identification Appels Entrants',
      description: 'Pays, type, opérateur, score de risque AVANT décroché',
      enabled: true,
      status: 'DEMO',
    },
    {
      id: 'popup_alert',
      icon: '🚨',
      name: 'Popup d\'Alerte Intelligente',
      description: 'Actions: Répondre, Bloquer, Assistant IA',
      enabled: true,
      status: 'DEMO',
    },
    {
      id: 'call_history',
      icon: '📜',
      name: 'Historique Enrichi',
      description: 'Historique avec scores, rapports IA, filtres',
      enabled: true,
      status: 'DEMO',
    },
    {
      id: 'ai_assistant',
      icon: '🤖',
      name: 'Répondeur IA Simulé',
      description: 'Dialogue neutre, analyse comportementale, rapport post-appel',
      enabled: aiAssistantEnabled,
      status: 'DEMO',
    },
    {
      id: 'zero_interaction',
      icon: '🎯',
      name: 'Mode Zéro Interaction',
      description: 'Blocage automatique sans intervention',
      enabled: zeroInteractionEnabled,
      status: 'DEMO',
    },
    {
      id: 'institution',
      icon: '🏢',
      name: 'Mode Institution',
      description: 'Journal d\'audit, lecture seule, conformité',
      enabled: institutionModeEnabled,
      status: 'DEMO',
    },
    {
      id: 'contacts',
      icon: '👥',
      name: 'Accès Contacts',
      description: 'Access contacts for caller ID enrichment',
      enabled: isFeatureEnabled('PHONE_CONTACTS_ACCESS'),
      permission: 'READ_CONTACTS',
      status: 'PERMISSION_REQUIRED',
    },
    {
      id: 'call_log',
      icon: '📞',
      name: 'Analyse Journal d\'Appels',
      description: 'Analyze call history for spam/scam detection',
      enabled: isFeatureEnabled('PHONE_CALL_LOG_ACCESS'),
      permission: 'READ_CALL_LOG',
      status: 'PERMISSION_REQUIRED',
    },
    {
      id: 'sms',
      icon: '💬',
      name: 'Lecture SMS (Read Only)',
      description: 'Read SMS for phishing detection',
      enabled: isFeatureEnabled('PHONE_SMS_READ_ACCESS'),
      permission: 'READ_SMS',
      status: 'PERMISSION_REQUIRED',
    },
  ];

  const handleFeaturePress = (feature: typeof features[0]) => {
    if (feature.id === 'call_identification' || feature.id === 'popup_alert') {
      // Démo de l'identification d'appel
      const demoNumber = '0162345678'; // Numéro ARCEP démarchage
      const identification = callIdentificationService.identifyCall(demoNumber);
      setDemoCallInfo(identification);
      setShowCallDemo(true);
      return;
    }
    
    if (feature.id === 'call_history') {
      navigation.navigate('CallHistory');
      return;
    }
    
    if (feature.id === 'ai_assistant') {
      Alert.alert(
        'Répondeur IA Simulé',
        'L\'assistant IA peut répondre aux appels à votre place.\n\n' +
        'SIMULATION: Dialogue neutre, analyse comportementale, rapport post-appel.\n\n' +
        'Activé: ' + (aiAssistantEnabled ? 'OUI' : 'NON'),
        [{ text: 'OK' }]
      );
      return;
    }
    
    if (feature.id === 'zero_interaction') {
      Alert.alert(
        'Mode Zéro Interaction',
        'Blocage automatique des appels suspects sans intervention.\n\n' +
        'DÉMO: Configuration du seuil de blocage automatique.\n\n' +
        'Activé: ' + (zeroInteractionEnabled ? 'OUI' : 'NON'),
        [{ text: 'OK' }]
      );
      return;
    }
    
    if (feature.id === 'institution') {
      Alert.alert(
        'Mode Institution',
        'Journal d\'audit complet, mode lecture seule, conformité entreprise.\n\n' +
        'DÉMO: Journalisation de toutes les actions.\n\n' +
        'Activé: ' + (institutionModeEnabled ? 'OUI' : 'NON'),
        [{ text: 'OK' }]
      );
      return;
    }
    
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
      case 'DEMO':
        return '#8b5cf6';
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
      case 'DEMO':
        return 'Démo';
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
            ⚠️ Information Importante
          </Text>
          <Text style={[styles.infoText, isDarkMode && styles.textDark]}>
            Module téléphone avec fonctionnalités de sécurité avancées.
            {'\n\n'}
            • Identification appels entrants (pays, opérateur, score risque)
            {'\n'}
            • Popup d'alerte AVANT décroché
            {'\n'}
            • Blocage intelligent (temporaire/définitif)
            {'\n'}
            • Répondeur IA simulé avec rapport post-appel
            {'\n'}
            • Mode Zéro Interaction (automatique)
            {'\n'}
            • Mode Institution (audit, lecture seule)
            {'\n\n'}
            IMPORTANT: Toutes les données restent locales. AUCUN spyware.
            {'\n\n'}
            État: Les fonctions marquées "Démo" sont fonctionnelles en simulation.
          </Text>
        </View>

        {/* Modes de fonctionnement */}
        <View style={styles.modesSection}>
          <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
            Modes de Fonctionnement
          </Text>
          
          <View style={[styles.modeCard, { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' }]}>
            <View style={styles.modeHeader}>
              <Text style={styles.modeIcon}>🤖</Text>
              <View style={styles.modeInfo}>
                <Text style={[styles.modeName, isDarkMode && styles.textDark]}>
                  Assistant IA
                </Text>
                <Text style={[styles.modeDescription, isDarkMode && styles.textDarkMuted]}>
                  Répondre aux appels automatiquement
                </Text>
              </View>
              <Switch
                value={aiAssistantEnabled}
                onValueChange={setAIAssistantEnabled}
                trackColor={{ false: '#767577', true: '#8b5cf6' }}
                thumbColor={aiAssistantEnabled ? '#ffffff' : '#f4f3f4'}
              />
            </View>
          </View>

          <View style={[styles.modeCard, { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' }]}>
            <View style={styles.modeHeader}>
              <Text style={styles.modeIcon}>🎯</Text>
              <View style={styles.modeInfo}>
                <Text style={[styles.modeName, isDarkMode && styles.textDark]}>
                  Zéro Interaction
                </Text>
                <Text style={[styles.modeDescription, isDarkMode && styles.textDarkMuted]}>
                  Blocage automatique sans intervention
                </Text>
              </View>
              <Switch
                value={zeroInteractionEnabled}
                onValueChange={setZeroInteractionEnabled}
                trackColor={{ false: '#767577', true: '#8b5cf6' }}
                thumbColor={zeroInteractionEnabled ? '#ffffff' : '#f4f3f4'}
              />
            </View>
          </View>

          <View style={[styles.modeCard, { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' }]}>
            <View style={styles.modeHeader}>
              <Text style={styles.modeIcon}>🏢</Text>
              <View style={styles.modeInfo}>
                <Text style={[styles.modeName, isDarkMode && styles.textDark]}>
                  Mode Institution
                </Text>
                <Text style={[styles.modeDescription, isDarkMode && styles.textDarkMuted]}>
                  Audit log, lecture seule, conformité
                </Text>
              </View>
              <Switch
                value={institutionModeEnabled}
                onValueChange={setInstitutionModeEnabled}
                trackColor={{ false: '#767577', true: '#8b5cf6' }}
                thumbColor={institutionModeEnabled ? '#ffffff' : '#f4f3f4'}
              />
            </View>
          </View>
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
            Phase B - Module Téléphone
          </Text>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            Conforme Google Play • AUCUN spyware • AUCUNE interception globale
          </Text>
          <Text style={[styles.footerText, isDarkMode && styles.textDarkMuted]}>
            Toutes les fonctionnalités sont explicables et transparentes
          </Text>
        </View>
      </ScrollView>

      {/* Popup d'alerte pour appel entrant (Démo) */}
      {showCallDemo && demoCallInfo && (
        <IncomingCallAlert
          visible={showCallDemo}
          callInfo={demoCallInfo}
          onAnswer={() => {
            setShowCallDemo(false);
            Alert.alert('Démo', 'Appel accepté - En mode démo uniquement');
          }}
          onBlock={() => {
            setShowCallDemo(false);
            Alert.alert('Démo', 'Appel bloqué temporairement - En mode démo uniquement');
          }}
          onBlockPermanent={() => {
            setShowCallDemo(false);
            Alert.alert('Démo', 'Numéro bloqué définitivement - En mode démo uniquement');
          }}
          onAIAssistant={aiAssistantEnabled ? () => {
            setShowCallDemo(false);
            Alert.alert('Démo', 'Assistant IA activé - En mode démo uniquement\n\nL\'IA répondrait à l\'appel et générerait un rapport post-appel.');
          } : undefined}
          onDismiss={() => setShowCallDemo(false)}
        />
      )}
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
  modesSection: {
    marginTop: 20,
    marginBottom: 20,
  },
  modeCard: {
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  modeHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  modeIcon: {
    fontSize: 32,
    marginRight: 12,
  },
  modeInfo: {
    flex: 1,
  },
  modeName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#2c3e50',
    marginBottom: 4,
  },
  modeDescription: {
    fontSize: 13,
    color: '#7f8c8d',
  },
});

export default PhoneScreen;
