/**
 * POPUP D'ALERTE - Appel Entrant
 * 
 * Affiche avant le décroché:
 * - Identification du pays
 * - Type de numéro
 * - Opérateur
 * - Score de risque
 * - Actions possibles
 * 
 * IMPORTANT:
 * - Aucun blocage automatique sans consentement
 * - Toutes les décisions restent à l'utilisateur
 * - Transparence totale sur les raisons du score
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  Modal,
  TouchableOpacity,
  ScrollView,
} from 'react-native';
import { CallIdentification } from '../modules/phone/CallIdentification';
import { getRiskColor, getRiskLabel, getRiskIcon, getNumberTypeLabel } from '../modules/phone/phoneUtils';

export interface IncomingCallAlertProps {
  visible: boolean;
  callInfo: CallIdentification | null;
  onAnswer: () => void;
  onBlock: () => void;
  onBlockPermanent: () => void;
  onAIAssistant?: () => void;  // Optionnel
  onDismiss: () => void;
}

const IncomingCallAlert: React.FC<IncomingCallAlertProps> = ({
  visible,
  callInfo,
  onAnswer,
  onBlock,
  onBlockPermanent,
  onAIAssistant,
  onDismiss,
}) => {
  if (!callInfo) return null;

  const getRiskColor = (level: string): string => {
    switch (level) {
      case 'CRITICAL': return '#dc2626';
      case 'HIGH': return '#ea580c';
      case 'MEDIUM': return '#f59e0b';
      case 'LOW': return '#84cc16';
      case 'SAFE': return '#10b981';
      default: return '#6b7280';
    }
  };

  const getRiskLabel = (level: string): string => {
    switch (level) {
      case 'CRITICAL': return 'CRITIQUE';
      case 'HIGH': return 'ÉLEVÉ';
      case 'MEDIUM': return 'MOYEN';
      case 'LOW': return 'FAIBLE';
      case 'SAFE': return 'SÛR';
      default: return 'INCONNU';
    }
  };

  const getRiskIcon = (level: string): string => {
    switch (level) {
      case 'CRITICAL': return '🚨';
      case 'HIGH': return '⚠️';
      case 'MEDIUM': return '⚡';
      case 'LOW': return '✓';
      case 'SAFE': return '✅';
      default: return '❓';
    }
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onDismiss}
    >
      <View style={styles.overlay}>
        <View style={styles.alertContainer}>
          {/* Header avec score de risque */}
          <View
            style={[
              styles.header,
              { backgroundColor: getRiskColor(callInfo.riskScore.level) },
            ]}
          >
            <Text style={styles.headerIcon}>
              {getRiskIcon(callInfo.riskScore.level)}
            </Text>
            <Text style={styles.headerTitle}>Appel Entrant</Text>
            <View style={styles.scoreContainer}>
              <Text style={styles.scoreText}>
                {callInfo.riskScore.total}/100
              </Text>
              <Text style={styles.scoreLabelText}>
                Risque {getRiskLabel(callInfo.riskScore.level)}
              </Text>
            </View>
          </View>

          {/* Informations d'identification */}
          <ScrollView style={styles.content}>
            {/* Numéro */}
            <View style={styles.infoSection}>
              <Text style={styles.phoneNumber}>
                {callInfo.isHidden ? 'Numéro Masqué' : callInfo.phoneNumber}
              </Text>
            </View>

            {/* Pays */}
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Pays:</Text>
              <View style={styles.infoValueContainer}>
                <Text style={styles.infoFlag}>{callInfo.country.flag}</Text>
                <Text style={styles.infoValue}>{callInfo.country.name}</Text>
              </View>
            </View>

            {/* Type de numéro */}
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Type:</Text>
              <Text style={styles.infoValue}>
                {getNumberTypeLabel(callInfo.numberType)}
              </Text>
            </View>

            {/* Opérateur */}
            {callInfo.operator && (
              <View style={styles.infoRow}>
                <Text style={styles.infoLabel}>Opérateur:</Text>
                <Text style={styles.infoValue}>{callInfo.operator.name}</Text>
              </View>
            )}

            {/* ARCEP Info */}
            {callInfo.arcepInfo?.isArcepRange && (
              <View style={styles.warningBox}>
                <Text style={styles.warningIcon}>📢</Text>
                <Text style={styles.warningText}>
                  Numéro ARCEP démarchage commercial (France)
                </Text>
              </View>
            )}

            {/* Raisons du score */}
            {callInfo.riskScore.reasons.length > 0 && (
              <View style={styles.reasonsSection}>
                <Text style={styles.reasonsTitle}>Raisons du score:</Text>
                {callInfo.riskScore.reasons.map((reason, index) => (
                  <View key={index} style={styles.reasonRow}>
                    <Text style={styles.reasonBullet}>•</Text>
                    <Text style={styles.reasonText}>{reason}</Text>
                  </View>
                ))}
              </View>
            )}

            {/* Détails du score */}
            <View style={styles.scoreBreakdown}>
              <Text style={styles.scoreBreakdownTitle}>Détails du score:</Text>
              <ScoreBar
                label="Pays"
                value={callInfo.riskScore.factors.country}
                max={20}
              />
              <ScoreBar
                label="Opérateur"
                value={callInfo.riskScore.factors.operator}
                max={20}
              />
              <ScoreBar
                label="Type"
                value={callInfo.riskScore.factors.numberType}
                max={20}
              />
              <ScoreBar
                label="Pattern"
                value={callInfo.riskScore.factors.pattern}
                max={20}
              />
              <ScoreBar
                label="ARCEP"
                value={callInfo.riskScore.factors.arcep}
                max={20}
              />
            </View>
          </ScrollView>

          {/* Actions */}
          <View style={styles.actions}>
            {/* Répondre */}
            <TouchableOpacity
              style={[styles.button, styles.buttonAnswer]}
              onPress={onAnswer}
            >
              <Text style={styles.buttonIcon}>📞</Text>
              <Text style={styles.buttonText}>Répondre</Text>
            </TouchableOpacity>

            {/* Bloquer (temporaire) */}
            <TouchableOpacity
              style={[styles.button, styles.buttonBlock]}
              onPress={onBlock}
            >
              <Text style={styles.buttonIcon}>🚫</Text>
              <Text style={styles.buttonText}>Bloquer</Text>
            </TouchableOpacity>

            {/* Bloquer (définitif) */}
            <TouchableOpacity
              style={[styles.button, styles.buttonBlockPermanent]}
              onPress={onBlockPermanent}
            >
              <Text style={styles.buttonIcon}>⛔</Text>
              <Text style={styles.buttonText}>Bloquer Définitif</Text>
            </TouchableOpacity>

            {/* Assistant IA (optionnel) */}
            {onAIAssistant && (
              <TouchableOpacity
                style={[styles.button, styles.buttonAI]}
                onPress={onAIAssistant}
              >
                <Text style={styles.buttonIcon}>🤖</Text>
                <Text style={styles.buttonText}>Assistant IA</Text>
              </TouchableOpacity>
            )}
          </View>

          {/* Footer disclaimer */}
          <View style={styles.footer}>
            <Text style={styles.footerText}>
              Analyse locale uniquement • Aucune donnée envoyée
            </Text>
          </View>
        </View>
      </View>
    </Modal>
  );
};

/**
 * Composant barre de score
 */
const ScoreBar: React.FC<{ label: string; value: number; max: number }> = ({
  label,
  value,
  max,
}) => {
  const percentage = (value / max) * 100;
  const color =
    percentage >= 75
      ? '#dc2626'
      : percentage >= 50
      ? '#f59e0b'
      : percentage >= 25
      ? '#84cc16'
      : '#10b981';

  return (
    <View style={styles.scoreBarContainer}>
      <Text style={styles.scoreBarLabel}>{label}</Text>
      <View style={styles.scoreBarTrack}>
        <View
          style={[
            styles.scoreBarFill,
            { width: `${percentage}%`, backgroundColor: color },
          ]}
        />
      </View>
      <Text style={styles.scoreBarValue}>
        {value}/{max}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  alertContainer: {
    width: '100%',
    maxWidth: 500,
    maxHeight: '90%',
    backgroundColor: '#1a1a1a',
    borderRadius: 16,
    overflow: 'hidden',
    elevation: 10,
  },
  header: {
    padding: 20,
    alignItems: 'center',
  },
  headerIcon: {
    fontSize: 48,
    marginBottom: 8,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 12,
  },
  scoreContainer: {
    alignItems: 'center',
  },
  scoreText: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#ffffff',
  },
  scoreLabelText: {
    fontSize: 14,
    color: '#ffffff',
    opacity: 0.9,
  },
  content: {
    maxHeight: 400,
    padding: 20,
  },
  infoSection: {
    marginBottom: 20,
    alignItems: 'center',
  },
  phoneNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#ffffff',
    textAlign: 'center',
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#333',
  },
  infoLabel: {
    fontSize: 14,
    color: '#9ca3af',
    fontWeight: '600',
  },
  infoValueContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  infoFlag: {
    fontSize: 20,
  },
  infoValue: {
    fontSize: 14,
    color: '#ffffff',
    fontWeight: '500',
  },
  warningBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(251, 191, 36, 0.15)',
    borderLeftWidth: 4,
    borderLeftColor: '#fbbf24',
    borderRadius: 8,
    padding: 12,
    marginVertical: 16,
  },
  warningIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  warningText: {
    flex: 1,
    fontSize: 13,
    color: '#fbbf24',
    fontWeight: '600',
  },
  reasonsSection: {
    marginTop: 16,
  },
  reasonsTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: 8,
  },
  reasonRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 6,
  },
  reasonBullet: {
    fontSize: 16,
    color: '#9ca3af',
    marginRight: 8,
    marginTop: 2,
  },
  reasonText: {
    flex: 1,
    fontSize: 13,
    color: '#d1d5db',
    lineHeight: 18,
  },
  scoreBreakdown: {
    marginTop: 20,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: '#333',
  },
  scoreBreakdownTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: 12,
  },
  scoreBarContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
    gap: 8,
  },
  scoreBarLabel: {
    fontSize: 12,
    color: '#9ca3af',
    width: 70,
  },
  scoreBarTrack: {
    flex: 1,
    height: 8,
    backgroundColor: '#333',
    borderRadius: 4,
    overflow: 'hidden',
  },
  scoreBarFill: {
    height: '100%',
    borderRadius: 4,
  },
  scoreBarValue: {
    fontSize: 11,
    color: '#9ca3af',
    width: 40,
    textAlign: 'right',
  },
  actions: {
    padding: 16,
    gap: 10,
  },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 14,
    borderRadius: 12,
    gap: 8,
  },
  buttonAnswer: {
    backgroundColor: '#10b981',
  },
  buttonBlock: {
    backgroundColor: '#f59e0b',
  },
  buttonBlockPermanent: {
    backgroundColor: '#dc2626',
  },
  buttonAI: {
    backgroundColor: '#8b5cf6',
  },
  buttonIcon: {
    fontSize: 20,
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#ffffff',
  },
  footer: {
    padding: 12,
    backgroundColor: '#0f0f0f',
    alignItems: 'center',
  },
  footerText: {
    fontSize: 11,
    color: '#6b7280',
    textAlign: 'center',
  },
});

export default IncomingCallAlert;
