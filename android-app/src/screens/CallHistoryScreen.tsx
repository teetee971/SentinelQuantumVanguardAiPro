/**
 * HISTORIQUE ENRICHI DES APPELS
 * 
 * Affiche l'historique avec:
 * - Score de risque pour chaque appel
 * - Identification pays/opérateur
 * - Actions prises (bloqué, répondu, IA)
 * - Rapports post-appel IA (si disponibles)
 * - Filtrage et recherche
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  TextInput,
  ScrollView,
  Modal,
} from 'react-native';
import { CallIdentification } from '../modules/phone/CallIdentification';
import { PostCallReport } from '../modules/phone/AIAssistant';
import { 
  getRiskColor, 
  formatDuration, 
  formatTimestamp, 
  getActionIcon, 
  getActionLabel 
} from '../modules/phone/phoneUtils';

export interface EnrichedCallEntry {
  id: string;
  timestamp: number;
  phoneNumber: string;
  identification: CallIdentification;
  direction: 'INCOMING' | 'OUTGOING' | 'MISSED';
  duration: number;           // Secondes
  action: 'ANSWERED' | 'BLOCKED' | 'AI_ANSWERED' | 'MISSED' | 'REJECTED';
  aiReport?: PostCallReport;  // Si répondu par IA
  userNotes?: string;
}

interface CallHistoryScreenProps {
  onBack?: () => void;
}

const CallHistoryScreen: React.FC<CallHistoryScreenProps> = ({ onBack }) => {
  const [calls, setCalls] = useState<EnrichedCallEntry[]>([]);
  const [filteredCalls, setFilteredCalls] = useState<EnrichedCallEntry[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCall, setSelectedCall] = useState<EnrichedCallEntry | null>(null);
  const [filterRisk, setFilterRisk] = useState<'ALL' | 'HIGH' | 'SAFE'>('ALL');
  
  useEffect(() => {
    // Charger l'historique (simulation)
    loadCallHistory();
  }, []);
  
  useEffect(() => {
    // Filtrer les appels
    filterCalls();
  }, [calls, searchQuery, filterRisk]);
  
  const loadCallHistory = () => {
    // TODO: Charger depuis stockage local
    // Pour l'instant, données de démo
    const demoData: EnrichedCallEntry[] = [
      // Appel bloqué - risque élevé
      {
        id: '1',
        timestamp: Date.now() - 3600000,
        phoneNumber: '0162345678',
        identification: {
          phoneNumber: '0162345678',
          country: { code: 'FR', name: 'France', callingCode: '+33', flag: '🇫🇷', confidence: 100 },
          numberType: 'voip' as any,
          operator: { name: 'VoIP / Box Internet', type: 'mvno', confidence: 80, isKnownSpammer: true },
          riskScore: { total: 75, factors: { country: 0, operator: 15, numberType: 12, pattern: 10, arcep: 15 }, level: 'HIGH', reasons: ['Numéro ARCEP démarchage commercial'] },
          arcepInfo: { isArcepRange: true, prefix: '0162', category: 'TELEMARKETING', confidence: 90, explanation: '' },
          isInternational: false,
          isHidden: false,
        },
        direction: 'INCOMING',
        duration: 0,
        action: 'BLOCKED',
      },
      // Appel répondu - risque faible
      {
        id: '2',
        timestamp: Date.now() - 7200000,
        phoneNumber: '0601234567',
        identification: {
          phoneNumber: '0601234567',
          country: { code: 'FR', name: 'France', callingCode: '+33', flag: '🇫🇷', confidence: 100 },
          numberType: 'mobile' as any,
          operator: { name: 'Mobile (Orange/SFR/Bouygues/Free)', type: 'mnc', confidence: 80, isKnownSpammer: false },
          riskScore: { total: 10, factors: { country: 0, operator: 0, numberType: 0, pattern: 5, arcep: 0 }, level: 'SAFE', reasons: ['Aucun indicateur de risque détecté'] },
          arcepInfo: null,
          isInternational: false,
          isHidden: false,
        },
        direction: 'INCOMING',
        duration: 120,
        action: 'ANSWERED',
      },
      // Appel IA - risque moyen
      {
        id: '3',
        timestamp: Date.now() - 10800000,
        phoneNumber: '+212612345678',
        identification: {
          phoneNumber: '212612345678',
          country: { code: '212', name: 'Maroc', callingCode: '+212', flag: '🇲🇦', confidence: 90 },
          numberType: 'mobile' as any,
          operator: null,
          riskScore: { total: 55, factors: { country: 15, operator: 0, numberType: 0, pattern: 5, arcep: 0 }, level: 'MEDIUM', reasons: ['Appel depuis Maroc (risque élevé de spam)'] },
          arcepInfo: null,
          isInternational: true,
          isHidden: false,
        },
        direction: 'INCOMING',
        duration: 25,
        action: 'AI_ANSWERED',
        aiReport: {
          callId: 'call_123',
          phoneNumber: '+212612345678',
          duration: 25,
          startTime: Date.now() - 10800000,
          endTime: Date.now() - 10799975,
          dialogue: [],
          behavioralAnalysis: {
            callerType: 'human',
            confidence: 75,
            patterns: {
              repeatedPhrases: [],
              pressureTactics: true,
              urgencyLevel: 7,
              requestedInfo: ['carte'],
              suspiciousKeywords: ['gratuit'],
            },
            sentiment: 'negative',
            scamIndicators: 65,
          },
          recommendation: 'BLOCK',
          summary: 'Appel commercial avec tactiques de pression. Demande d\'informations sensibles. Bloquer recommandé.',
        },
      },
    ];
    
    setCalls(demoData);
  };
  
  const filterCalls = () => {
    let filtered = [...calls];
    
    // Filtre par recherche
    if (searchQuery) {
      filtered = filtered.filter(call =>
        call.phoneNumber.includes(searchQuery) ||
        call.identification.country.name.toLowerCase().includes(searchQuery.toLowerCase())
      );
    }
    
    // Filtre par risque
    if (filterRisk === 'HIGH') {
      filtered = filtered.filter(call =>
        call.identification.riskScore.level === 'HIGH' ||
        call.identification.riskScore.level === 'CRITICAL'
      );
    } else if (filterRisk === 'SAFE') {
      filtered = filtered.filter(call =>
        call.identification.riskScore.level === 'SAFE' ||
        call.identification.riskScore.level === 'LOW'
      );
    }
    
    setFilteredCalls(filtered);
  };
  
  const renderCallItem = ({ item }: { item: EnrichedCallEntry }) => (
    <TouchableOpacity
      style={styles.callCard}
      onPress={() => setSelectedCall(item)}
    >
      <View style={styles.callHeader}>
        <View style={styles.callInfo}>
          <Text style={styles.callNumber}>{item.phoneNumber}</Text>
          <View style={styles.callMeta}>
            <Text style={styles.callFlag}>{item.identification.country.flag}</Text>
            <Text style={styles.callMetaText}>{item.identification.country.name}</Text>
            <Text style={styles.callDot}>•</Text>
            <Text style={styles.callMetaText}>{formatTimestamp(item.timestamp)}</Text>
          </View>
        </View>
        <View style={styles.callActions}>
          <Text style={styles.actionIcon}>{getActionIcon(item.action)}</Text>
        </View>
      </View>
      
      <View style={styles.callDetails}>
        <View
          style={[
            styles.riskBadge,
            { backgroundColor: `${getRiskColor(item.identification.riskScore.level)}20` },
          ]}
        >
          <Text
            style={[
              styles.riskText,
              { color: getRiskColor(item.identification.riskScore.level) },
            ]}
          >
            Risque: {item.identification.riskScore.total}/100
          </Text>
        </View>
        
        {item.duration > 0 && (
          <View style={styles.durationBadge}>
            <Text style={styles.durationText}>⏱️ {formatDuration(item.duration)}</Text>
          </View>
        )}
        
        {item.aiReport && (
          <View style={styles.aiBadge}>
            <Text style={styles.aiText}>Rapport IA disponible</Text>
          </View>
        )}
      </View>
    </TouchableOpacity>
  );
  
  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Historique des Appels</Text>
        <Text style={styles.headerSubtitle}>{filteredCalls.length} appels</Text>
      </View>
      
      {/* Recherche */}
      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="Rechercher un numéro..."
          placeholderTextColor="#6b7280"
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
      </View>
      
      {/* Filtres */}
      <View style={styles.filtersContainer}>
        <TouchableOpacity
          style={[styles.filterButton, filterRisk === 'ALL' && styles.filterButtonActive]}
          onPress={() => setFilterRisk('ALL')}
        >
          <Text style={[styles.filterText, filterRisk === 'ALL' && styles.filterTextActive]}>
            Tous
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.filterButton, filterRisk === 'HIGH' && styles.filterButtonActive]}
          onPress={() => setFilterRisk('HIGH')}
        >
          <Text style={[styles.filterText, filterRisk === 'HIGH' && styles.filterTextActive]}>
            Risque Élevé
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.filterButton, filterRisk === 'SAFE' && styles.filterButtonActive]}
          onPress={() => setFilterRisk('SAFE')}
        >
          <Text style={[styles.filterText, filterRisk === 'SAFE' && styles.filterTextActive]}>
            Sûrs
          </Text>
        </TouchableOpacity>
      </View>
      
      {/* Liste */}
      <FlatList
        data={filteredCalls}
        renderItem={renderCallItem}
        keyExtractor={item => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>Aucun appel trouvé</Text>
          </View>
        }
      />
      
      {/* Modal détails */}
      {selectedCall && (
        <CallDetailModal
          call={selectedCall}
          visible={true}
          onClose={() => setSelectedCall(null)}
        />
      )}
    </View>
  );
};

/**
 * Modal détails d'un appel
 */
const CallDetailModal: React.FC<{
  call: EnrichedCallEntry;
  visible: boolean;
  onClose: () => void;
}> = ({ call, visible, onClose }) => {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <View style={styles.modalOverlay}>
        <View style={styles.modalContainer}>
          <ScrollView style={styles.modalContent}>
            <Text style={styles.modalTitle}>Détails de l'Appel</Text>
            
            <View style={styles.modalSection}>
              <Text style={styles.modalLabel}>Numéro:</Text>
              <Text style={styles.modalValue}>{call.phoneNumber}</Text>
            </View>
            
            <View style={styles.modalSection}>
              <Text style={styles.modalLabel}>Pays:</Text>
              <Text style={styles.modalValue}>
                {call.identification.country.flag} {call.identification.country.name}
              </Text>
            </View>
            
            <View style={styles.modalSection}>
              <Text style={styles.modalLabel}>Type:</Text>
              <Text style={styles.modalValue}>{call.identification.numberType}</Text>
            </View>
            
            {call.identification.operator && (
              <View style={styles.modalSection}>
                <Text style={styles.modalLabel}>Opérateur:</Text>
                <Text style={styles.modalValue}>{call.identification.operator.name}</Text>
              </View>
            )}
            
            <View style={styles.modalSection}>
              <Text style={styles.modalLabel}>Score de Risque:</Text>
              <Text style={[styles.modalValue, { color: getRiskColor(call.identification.riskScore.level) }]}>
                {call.identification.riskScore.total}/100 ({call.identification.riskScore.level})
              </Text>
            </View>
            
            <View style={styles.modalSection}>
              <Text style={styles.modalLabel}>Raisons:</Text>
              {call.identification.riskScore.reasons.map((reason, index) => (
                <Text key={index} style={styles.modalReasonText}>• {reason}</Text>
              ))}
            </View>
            
            {call.aiReport && (
              <View style={styles.modalSection}>
                <Text style={styles.modalLabel}>Rapport IA:</Text>
                <Text style={styles.modalValue}>{call.aiReport.summary}</Text>
                <Text style={styles.modalSubLabel}>
                  Recommandation: {call.aiReport.recommendation}
                </Text>
              </View>
            )}
          </ScrollView>
          
          <TouchableOpacity style={styles.closeButton} onPress={onClose}>
            <Text style={styles.closeButtonText}>Fermer</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f0f0f',
  },
  header: {
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#333',
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#ffffff',
  },
  headerSubtitle: {
    fontSize: 14,
    color: '#9ca3af',
    marginTop: 4,
  },
  searchContainer: {
    padding: 16,
  },
  searchInput: {
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    padding: 14,
    color: '#ffffff',
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#333',
  },
  filtersContainer: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    gap: 10,
    marginBottom: 16,
  },
  filterButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#1a1a1a',
    borderWidth: 1,
    borderColor: '#333',
  },
  filterButtonActive: {
    backgroundColor: '#8b5cf6',
    borderColor: '#8b5cf6',
  },
  filterText: {
    fontSize: 14,
    color: '#9ca3af',
    fontWeight: '600',
  },
  filterTextActive: {
    color: '#ffffff',
  },
  listContent: {
    padding: 16,
  },
  callCard: {
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#333',
  },
  callHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  callInfo: {
    flex: 1,
  },
  callNumber: {
    fontSize: 18,
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: 6,
  },
  callMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  callFlag: {
    fontSize: 16,
  },
  callMetaText: {
    fontSize: 13,
    color: '#9ca3af',
  },
  callDot: {
    fontSize: 13,
    color: '#6b7280',
  },
  callActions: {
    alignItems: 'center',
  },
  actionIcon: {
    fontSize: 28,
  },
  callDetails: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  riskBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  riskText: {
    fontSize: 12,
    fontWeight: '700',
  },
  durationBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    backgroundColor: '#1e293b',
  },
  durationText: {
    fontSize: 12,
    color: '#94a3b8',
  },
  aiBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    backgroundColor: 'rgba(139, 92, 246, 0.2)',
  },
  aiText: {
    fontSize: 12,
    color: '#a78bfa',
    fontWeight: '600',
  },
  emptyContainer: {
    padding: 40,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#6b7280',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.9)',
    justifyContent: 'flex-end',
  },
  modalContainer: {
    backgroundColor: '#1a1a1a',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    maxHeight: '80%',
  },
  modalContent: {
    padding: 24,
  },
  modalTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 24,
  },
  modalSection: {
    marginBottom: 20,
  },
  modalLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#9ca3af',
    marginBottom: 6,
  },
  modalValue: {
    fontSize: 16,
    color: '#ffffff',
  },
  modalSubLabel: {
    fontSize: 13,
    color: '#9ca3af',
    marginTop: 6,
  },
  modalReasonText: {
    fontSize: 14,
    color: '#d1d5db',
    marginTop: 4,
  },
  closeButton: {
    margin: 16,
    padding: 16,
    backgroundColor: '#8b5cf6',
    borderRadius: 12,
    alignItems: 'center',
  },
  closeButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#ffffff',
  },
});

export default CallHistoryScreen;
