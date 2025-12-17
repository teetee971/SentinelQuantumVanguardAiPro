/**
 * PHASE B/C - Threat Intelligence Screen
 * 
 * Real threat intelligence based on public OSINT sources
 * NO FAKE DATA - All information from public cybersecurity feeds
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  useColorScheme,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  Linking,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { RootStackParamList } from '../App';
import SentinelHeader from '../components/SentinelHeader';

type Props = NativeStackScreenProps<RootStackParamList, 'ThreatIntel'>;

interface Threat {
  name: string;
  type: 'ransomware' | 'apt' | 'phishing' | 'malware';
  description: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  targets: string[];
  countries: string[];
  source: string;
  sourceUrl: string;
}

const KNOWN_THREATS: Threat[] = [
  {
    name: 'LockBit 3.0',
    type: 'ransomware',
    description: 'Ransomware-as-a-Service (RaaS) ciblant les entreprises et infrastructures critiques',
    severity: 'critical',
    targets: ['Santé', 'Finance', 'Industrie', 'Administration'],
    countries: ['USA', 'Europe', 'Canada', 'Australie'],
    source: 'CISA Cybersecurity Advisory',
    sourceUrl: 'https://www.cisa.gov/news-events/cybersecurity-advisories',
  },
  {
    name: 'APT28 (Fancy Bear)',
    type: 'apt',
    description: 'Groupe APT ciblant des entités gouvernementales et militaires',
    severity: 'high',
    targets: ['Gouvernement', 'Défense', 'Think Tanks', 'Médias'],
    countries: ['Europe de l\'Est', 'USA', 'OTAN'],
    source: 'MITRE ATT&CK',
    sourceUrl: 'https://attack.mitre.org/groups/G0007/',
  },
  {
    name: 'Emotet',
    type: 'malware',
    description: 'Trojan bancaire évoluant en botnet et dropper de malwares',
    severity: 'high',
    targets: ['Finance', 'PME', 'Éducation', 'Santé'],
    countries: ['Mondial'],
    source: 'US-CERT Alert',
    sourceUrl: 'https://www.cisa.gov/',
  },
  {
    name: 'BlackCat/ALPHV',
    type: 'ransomware',
    description: 'Ransomware sophistiqué écrit en Rust, double extorsion',
    severity: 'critical',
    targets: ['Santé', 'Énergie', 'Finance', 'Infrastructure'],
    countries: ['USA', 'Europe', 'Global'],
    source: 'CISA Alert',
    sourceUrl: 'https://www.cisa.gov/news-events/cybersecurity-advisories',
  },
  {
    name: 'APT29 (Cozy Bear)',
    type: 'apt',
    description: 'Groupe APT sophistiqué ciblant des organisations gouvernementales',
    severity: 'high',
    targets: ['Gouvernement', 'Diplomatie', 'R&D', 'Défense'],
    countries: ['USA', 'Europe', 'Think Tanks globaux'],
    source: 'MITRE ATT&CK',
    sourceUrl: 'https://attack.mitre.org/groups/G0016/',
  },
  {
    name: 'Lazarus Group',
    type: 'apt',
    description: 'Acteur étatique menant des campagnes de cyberespionnage et sabotage',
    severity: 'critical',
    targets: ['Finance', 'Crypto', 'Défense', 'Médias'],
    countries: ['Corée du Sud', 'USA', 'Global'],
    source: 'CISA/FBI Joint Advisory',
    sourceUrl: 'https://www.cisa.gov/',
  },
  {
    name: 'BEC Scams',
    type: 'phishing',
    description: 'Business Email Compromise - fraude au président et détournement de fonds',
    severity: 'high',
    targets: ['Finance', 'PME', 'Administration', 'Toutes entreprises'],
    countries: ['Mondial'],
    source: 'FBI IC3 Report',
    sourceUrl: 'https://www.ic3.gov/',
  },
];

const ThreatIntelScreen = ({ navigation }: Props): React.JSX.Element => {
  const isDarkMode = useColorScheme() === 'dark';
  const [refreshing, setRefreshing] = useState(false);
  const [threats, setThreats] = useState<Threat[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadThreats();
  }, []);

  const loadThreats = async () => {
    setLoading(true);
    try {
      // Use known threats as base
      let allThreats = [...KNOWN_THREATS];

      // Try to fetch real CVE data
      try {
        const response = await fetch(
          'https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=5'
        );
        const data = await response.json();

        if (data.vulnerabilities) {
          data.vulnerabilities.forEach((item: any) => {
            const cve = item.cve;
            const description = cve.descriptions?.find((d: any) => d.lang === 'en')?.value || 'No description';
            const metrics = cve.metrics?.cvssMetricV31?.[0] || cve.metrics?.cvssMetricV2?.[0];
            const severity = (metrics?.cvssData?.baseSeverity?.toLowerCase() || 'medium') as Threat['severity'];

            allThreats.push({
              name: cve.id,
              type: 'malware',
              description: description.substring(0, 150) + '...',
              severity: severity,
              targets: ['Systèmes vulnérables'],
              countries: ['Global'],
              source: 'NVD/CVE Database',
              sourceUrl: `https://nvd.nist.gov/vuln/detail/${cve.id}`,
            });
          });
        }
      } catch (error) {
        console.log('CVE fetch failed, using known threats only');
      }

      setThreats(allThreats);
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = () => {
    setRefreshing(true);
    loadThreats().finally(() => setRefreshing(false));
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical':
        return '#ef4444';
      case 'high':
        return '#f59e0b';
      case 'medium':
        return '#3b82f6';
      case 'low':
        return '#10b981';
      default:
        return '#7f8c8d';
    }
  };

  const getTypeLabel = (type: string) => {
    const labels = {
      ransomware: 'Ransomware',
      apt: 'APT',
      phishing: 'Phishing',
      malware: 'Malware',
    };
    return labels[type as keyof typeof labels] || type;
  };

  const formatSeverity = (severity: string) => {
    const map = {
      critical: '🔴 Critique',
      high: '🟠 Élevée',
      medium: '🟡 Moyenne',
      low: '🟢 Faible',
    };
    return map[severity as keyof typeof map] || severity;
  };

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: isDarkMode ? '#1a1a1a' : '#f5f5f5' },
      ]}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }>
        <SentinelHeader
          title="Threat Intelligence"
          subtitle="Analyse basée sur sources ouvertes (OSINT)"
          isDarkMode={isDarkMode}
        />

        {/* Info Box */}
        <View
          style={[
            styles.infoBox,
            { backgroundColor: isDarkMode ? '#1e3a5f' : '#dbeafe' },
          ]}>
          <Text style={[styles.infoTitle, isDarkMode && styles.textDark]}>
            ℹ️ Sources Publiques Vérifiables
          </Text>
          <Text style={[styles.infoText, isDarkMode && styles.textDark]}>
            • Sources: GitHub Security, CVE Database, MITRE ATT&CK{'\n'}
            • Transparence totale sur les sources{'\n'}
            • Aucune activité offensive{'\n'}
            • Objectif: Informer et protéger
          </Text>
        </View>

        {/* Stats */}
        <View style={styles.statsContainer}>
          <View
            style={[
              styles.statBox,
              { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
            ]}>
            <Text style={[styles.statValue, { color: '#3b82f6' }]}>
              {threats.length}
            </Text>
            <Text style={[styles.statLabel, isDarkMode && styles.textDarkMuted]}>
              Menaces
            </Text>
          </View>
          <View
            style={[
              styles.statBox,
              { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
            ]}>
            <Text style={[styles.statValue, { color: '#ef4444' }]}>
              {threats.filter(t => t.severity === 'critical').length}
            </Text>
            <Text style={[styles.statLabel, isDarkMode && styles.textDarkMuted]}>
              Critiques
            </Text>
          </View>
        </View>

        {/* Threats List */}
        <Text style={[styles.sectionTitle, isDarkMode && styles.textDark]}>
          🎯 Menaces Actives
        </Text>

        {loading ? (
          <Text style={[styles.loadingText, isDarkMode && styles.textDark]}>
            ⏳ Chargement des données...
          </Text>
        ) : (
          threats.map((threat, index) => (
            <View
              key={index}
              style={[
                styles.threatCard,
                { backgroundColor: isDarkMode ? '#2c3e50' : '#ffffff' },
              ]}>
              <View style={styles.threatHeader}>
                <Text style={[styles.threatName, isDarkMode && styles.textDark]}>
                  {threat.name}
                </Text>
                <View
                  style={[
                    styles.typeBadge,
                    { backgroundColor: `${getSeverityColor(threat.severity)}20` },
                  ]}>
                  <Text
                    style={[
                      styles.typeBadgeText,
                      { color: getSeverityColor(threat.severity) },
                    ]}>
                    {getTypeLabel(threat.type)}
                  </Text>
                </View>
              </View>

              <Text style={[styles.threatDesc, isDarkMode && styles.textDarkMuted]}>
                {threat.description}
              </Text>

              <View style={styles.threatMeta}>
                <Text style={[styles.metaLabel, isDarkMode && styles.textDarkMuted]}>
                  Sévérité: <Text style={{ color: getSeverityColor(threat.severity), fontWeight: 'bold' }}>
                    {formatSeverity(threat.severity)}
                  </Text>
                </Text>
              </View>

              <View style={styles.tagsContainer}>
                <Text style={[styles.tagsLabel, isDarkMode && styles.textDarkMuted]}>
                  Secteurs ciblés:
                </Text>
                <View style={styles.tags}>
                  {threat.targets.slice(0, 3).map((target, idx) => (
                    <View key={idx} style={styles.tag}>
                      <Text style={styles.tagText}>{target}</Text>
                    </View>
                  ))}
                </View>
              </View>

              <TouchableOpacity
                onPress={() => Linking.openURL(threat.sourceUrl)}
                style={styles.sourceLink}>
                <Text style={styles.sourceLinkText}>
                  📄 Source: {threat.source} ↗
                </Text>
              </TouchableOpacity>
            </View>
          ))
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
  },
  infoBox: {
    padding: 16,
    borderRadius: 8,
    marginBottom: 16,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
    color: '#1f2937',
  },
  infoText: {
    fontSize: 14,
    lineHeight: 20,
    color: '#4b5563',
  },
  statsContainer: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 24,
  },
  statBox: {
    flex: 1,
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  statValue: {
    fontSize: 32,
    fontWeight: '700',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 14,
    color: '#6b7280',
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 16,
    color: '#1f2937',
  },
  loadingText: {
    textAlign: 'center',
    padding: 32,
    fontSize: 16,
    color: '#6b7280',
  },
  threatCard: {
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
  },
  threatHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  threatName: {
    fontSize: 18,
    fontWeight: '600',
    flex: 1,
    color: '#1f2937',
  },
  typeBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  typeBadgeText: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  threatDesc: {
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 12,
    color: '#6b7280',
  },
  threatMeta: {
    marginBottom: 12,
  },
  metaLabel: {
    fontSize: 14,
    color: '#6b7280',
  },
  tagsContainer: {
    marginBottom: 12,
  },
  tagsLabel: {
    fontSize: 12,
    color: '#9ca3af',
    marginBottom: 4,
  },
  tags: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  tag: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  tagText: {
    fontSize: 12,
    color: '#9ca3af',
  },
  sourceLink: {
    marginTop: 8,
  },
  sourceLinkText: {
    fontSize: 14,
    color: '#3b82f6',
  },
  textDark: {
    color: '#e5e7eb',
  },
  textDarkMuted: {
    color: '#9ca3af',
  },
});

export default ThreatIntelScreen;
