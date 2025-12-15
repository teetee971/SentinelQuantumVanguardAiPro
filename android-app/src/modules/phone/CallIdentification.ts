/**
 * MODULE TÉLÉPHONE - Identification des Appels Entrants
 * 
 * Fonctionnalités:
 * 1. Identification pays d'origine
 * 2. Type de numéro (mobile, fixe, gratuit, premium)
 * 3. Opérateur téléphonique
 * 4. Score de risque AVANT décroché
 * 
 * IMPORTANT:
 * - Traitement 100% local
 * - Aucune API externe
 * - Aucun spyware
 * - Conforme RGPD et Google Play
 */

import { getArcepInfo, ArcepInfo } from './arcepRanges';

/**
 * Type de numéro de téléphone
 */
export enum PhoneNumberType {
  MOBILE = 'mobile',
  LANDLINE = 'fixe',
  TOLL_FREE = 'gratuit',
  PREMIUM = 'surtaxé',
  VOIP = 'voip',
  UNKNOWN = 'inconnu',
}

/**
 * Pays détectés (basé sur indicatif)
 */
export interface CountryInfo {
  code: string;           // Code pays (ex: 'FR', 'US')
  name: string;           // Nom du pays
  callingCode: string;    // Indicatif (ex: '+33')
  flag: string;           // Emoji drapeau
  confidence: number;     // 0-100% confiance
}

/**
 * Opérateur téléphonique détecté
 */
export interface OperatorInfo {
  name: string;           // Nom opérateur (ex: 'Orange', 'Bouygues')
  type: 'mvno' | 'mnc' | 'unknown';
  confidence: number;     // 0-100%
  isKnownSpammer: boolean; // Opérateur connu pour spam
}

/**
 * Score de risque détaillé
 */
export interface RiskScore {
  total: number;          // Score total 0-100 (0=sûr, 100=dangereux)
  factors: {
    country: number;      // Risque lié au pays (0-20)
    operator: number;     // Risque lié à l'opérateur (0-20)
    numberType: number;   // Risque lié au type de numéro (0-20)
    pattern: number;      // Risque lié au pattern du numéro (0-20)
    arcep: number;        // Risque ARCEP démarchage (0-20)
  };
  level: 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  reasons: string[];      // Raisons du score en français
}

/**
 * Identification complète d'un appel entrant
 */
export interface CallIdentification {
  phoneNumber: string;
  country: CountryInfo;
  numberType: PhoneNumberType;
  operator: OperatorInfo | null;
  riskScore: RiskScore;
  arcepInfo: ArcepInfo | null;
  isInternational: boolean;
  isHidden: boolean;      // Numéro masqué
  displayName?: string;   // Nom à afficher (si trouvé)
}

/**
 * Table des indicatifs internationaux
 */
const COUNTRY_CODES: Record<string, { name: string; flag: string; risk: number }> = {
  '1': { name: 'États-Unis/Canada', flag: '🇺🇸', risk: 5 },
  '33': { name: 'France', flag: '🇫🇷', risk: 0 },
  '44': { name: 'Royaume-Uni', flag: '🇬🇧', risk: 5 },
  '49': { name: 'Allemagne', flag: '🇩🇪', risk: 3 },
  '39': { name: 'Italie', flag: '🇮🇹', risk: 5 },
  '34': { name: 'Espagne', flag: '🇪🇸', risk: 5 },
  '32': { name: 'Belgique', flag: '🇧🇪', risk: 3 },
  '41': { name: 'Suisse', flag: '🇨🇭', risk: 3 },
  '212': { name: 'Maroc', flag: '🇲🇦', risk: 15 },
  '216': { name: 'Tunisie', flag: '🇹🇳', risk: 15 },
  '213': { name: 'Algérie', flag: '🇩🇿', risk: 15 },
  '91': { name: 'Inde', flag: '🇮🇳', risk: 18 },
  '86': { name: 'Chine', flag: '🇨🇳', risk: 12 },
  '234': { name: 'Nigéria', flag: '🇳🇬', risk: 20 },
  '254': { name: 'Kenya', flag: '🇰🇪', risk: 15 },
};

/**
 * Opérateurs français (détection basique par préfixe)
 */
const FRENCH_OPERATORS: Record<string, { name: string; isKnownSpammer: boolean }> = {
  '06': { name: 'Mobile (Orange/SFR/Bouygues/Free)', isKnownSpammer: false },
  '07': { name: 'Mobile (MVNO)', isKnownSpammer: false },
  '01': { name: 'Fixe Île-de-France', isKnownSpammer: false },
  '02': { name: 'Fixe Nord-Ouest', isKnownSpammer: false },
  '03': { name: 'Fixe Nord-Est', isKnownSpammer: false },
  '04': { name: 'Fixe Sud-Est', isKnownSpammer: false },
  '05': { name: 'Fixe Sud-Ouest', isKnownSpammer: false },
  '09': { name: 'VoIP / Box Internet', isKnownSpammer: true },
  '08': { name: 'Numéro spécial / Surtaxé', isKnownSpammer: true },
};

/**
 * Classe principale d'identification d'appels
 */
export class CallIdentificationService {
  
  /**
   * Identifier un appel entrant (avant décroché)
   * Traitement 100% local, aucune requête réseau
   */
  identifyCall(phoneNumber: string): CallIdentification {
    // Normaliser le numéro
    const normalized = this.normalizePhoneNumber(phoneNumber);
    
    // Vérifier numéro masqué
    const isHidden = this.isHiddenNumber(normalized);
    
    // Détecter pays
    const country = this.detectCountry(normalized);
    
    // Déterminer type de numéro
    const numberType = this.detectNumberType(normalized, country.code);
    
    // Détecter opérateur
    const operator = this.detectOperator(normalized, country.code);
    
    // Calculer score de risque
    const riskScore = this.calculateRiskScore(
      normalized,
      country,
      numberType,
      operator
    );
    
    // Vérifier ARCEP (France uniquement)
    const arcepInfo = country.code === 'FR' ? getArcepInfo(normalized) : null;
    
    // International?
    const isInternational = country.code !== 'FR' && country.code !== 'LOCAL';
    
    return {
      phoneNumber: normalized,
      country,
      numberType,
      operator,
      riskScore,
      arcepInfo,
      isInternational,
      isHidden,
    };
  }
  
  /**
   * Normaliser numéro de téléphone
   */
  private normalizePhoneNumber(phoneNumber: string): string {
    if (!phoneNumber) return '';
    
    // Enlever espaces, tirets, parenthèses, points
    let cleaned = phoneNumber.replace(/[\s\-\(\)\.\+]/g, '');
    
    // Si commence par 0033, convertir en +33
    if (cleaned.startsWith('0033')) {
      cleaned = '33' + cleaned.substring(4);
    }
    
    return cleaned;
  }
  
  /**
   * Vérifier si numéro masqué
   */
  private isHiddenNumber(number: string): boolean {
    if (!number || number.length === 0) return true;
    if (number === 'unknown' || number === 'anonymous') return true;
    if (number.match(/^0+$/)) return true; // Que des zéros
    return false;
  }
  
  /**
   * Détecter pays d'origine
   */
  private detectCountry(phoneNumber: string): CountryInfo {
    if (this.isHiddenNumber(phoneNumber)) {
      return {
        code: 'UNKNOWN',
        name: 'Numéro masqué',
        callingCode: '',
        flag: '❓',
        confidence: 100,
      };
    }
    
    // Vérifier numéro français (commence par 0 ou 33)
    if (phoneNumber.startsWith('0') && phoneNumber.length === 10) {
      return {
        code: 'FR',
        name: 'France',
        callingCode: '+33',
        flag: '🇫🇷',
        confidence: 100,
      };
    }
    
    // Vérifier indicatifs internationaux
    for (const [code, info] of Object.entries(COUNTRY_CODES)) {
      if (phoneNumber.startsWith(code)) {
        return {
          code: code,
          name: info.name,
          callingCode: '+' + code,
          flag: info.flag,
          confidence: 90,
        };
      }
    }
    
    return {
      code: 'LOCAL',
      name: 'Local/Inconnu',
      callingCode: '',
      flag: '📍',
      confidence: 50,
    };
  }
  
  /**
   * Détecter type de numéro
   */
  private detectNumberType(phoneNumber: string, countryCode: string): PhoneNumberType {
    if (this.isHiddenNumber(phoneNumber)) {
      return PhoneNumberType.UNKNOWN;
    }
    
    // France
    if (countryCode === 'FR') {
      const prefix = phoneNumber.substring(0, 2);
      
      if (prefix === '06' || prefix === '07') return PhoneNumberType.MOBILE;
      if (prefix === '08') return PhoneNumberType.PREMIUM;
      if (prefix === '09') return PhoneNumberType.VOIP;
      if (['01', '02', '03', '04', '05'].includes(prefix)) {
        return PhoneNumberType.LANDLINE;
      }
    }
    
    // Autres pays (détection basique)
    if (phoneNumber.length >= 10 && phoneNumber.length <= 15) {
      // Heuristique simple
      return PhoneNumberType.UNKNOWN;
    }
    
    return PhoneNumberType.UNKNOWN;
  }
  
  /**
   * Détecter opérateur
   */
  private detectOperator(phoneNumber: string, countryCode: string): OperatorInfo | null {
    if (countryCode !== 'FR') {
      return null; // Détection opérateur France uniquement
    }
    
    const prefix = phoneNumber.substring(0, 2);
    const operatorData = FRENCH_OPERATORS[prefix];
    
    if (operatorData) {
      return {
        name: operatorData.name,
        type: prefix === '09' ? 'mvno' : 'mnc',
        confidence: 80,
        isKnownSpammer: operatorData.isKnownSpammer,
      };
    }
    
    return null;
  }
  
  /**
   * Calculer score de risque AVANT décroché
   */
  private calculateRiskScore(
    phoneNumber: string,
    country: CountryInfo,
    numberType: PhoneNumberType,
    operator: OperatorInfo | null
  ): RiskScore {
    const factors = {
      country: 0,
      operator: 0,
      numberType: 0,
      pattern: 0,
      arcep: 0,
    };
    
    const reasons: string[] = [];
    
    // Facteur pays (0-20)
    if (country.code === 'UNKNOWN') {
      factors.country = 15;
      reasons.push('Numéro masqué ou inconnu');
    } else {
      const countryData = COUNTRY_CODES[country.code.replace('+', '')];
      if (countryData) {
        factors.country = Math.min(20, countryData.risk);
        if (countryData.risk > 10) {
          reasons.push(`Appel depuis ${country.name} (risque élevé de spam)`);
        }
      }
    }
    
    // Facteur opérateur (0-20)
    if (operator?.isKnownSpammer) {
      factors.operator = 15;
      reasons.push(`Type d'opérateur souvent utilisé pour démarchage`);
    }
    
    // Facteur type de numéro (0-20)
    if (numberType === PhoneNumberType.PREMIUM) {
      factors.numberType = 20;
      reasons.push('Numéro surtaxé (08XX)');
    } else if (numberType === PhoneNumberType.VOIP) {
      factors.numberType = 12;
      reasons.push('Numéro VoIP (09XX) - souvent utilisé par call centers');
    }
    
    // Facteur pattern (0-20)
    const patternRisk = this.analyzeNumberPattern(phoneNumber);
    factors.pattern = patternRisk;
    if (patternRisk > 10) {
      reasons.push('Pattern de numéro suspect (chiffres répétés ou séquence)');
    }
    
    // Facteur ARCEP (0-20)
    const arcepInfo = getArcepInfo(phoneNumber);
    if (arcepInfo.isArcepRange) {
      factors.arcep = 15;
      reasons.push('Numéro ARCEP démarchage commercial (France)');
    }
    
    // Score total
    const total = Math.min(100,
      factors.country +
      factors.operator +
      factors.numberType +
      factors.pattern +
      factors.arcep
    );
    
    // Niveau de risque
    let level: RiskScore['level'];
    if (total >= 80) level = 'CRITICAL';
    else if (total >= 60) level = 'HIGH';
    else if (total >= 40) level = 'MEDIUM';
    else if (total >= 20) level = 'LOW';
    else level = 'SAFE';
    
    // Ajouter raison si score faible
    if (total < 20) {
      reasons.push('Aucun indicateur de risque détecté');
    }
    
    return {
      total,
      factors,
      level,
      reasons,
    };
  }
  
  /**
   * Analyser pattern du numéro
   */
  private analyzeNumberPattern(phoneNumber: string): number {
    let risk = 0;
    
    // Chiffres tous identiques
    if (phoneNumber.match(/^(\d)\1+$/)) {
      risk += 20;
    }
    
    // Séquence ascendante/descendante
    if (this.isSequence(phoneNumber)) {
      risk += 15;
    }
    
    // Trop de chiffres répétés
    const repeatedCount = this.countRepeatedDigits(phoneNumber);
    if (repeatedCount > 5) {
      risk += 10;
    }
    
    return Math.min(20, risk);
  }
  
  /**
   * Vérifier si séquence
   */
  private isSequence(number: string): boolean {
    if (number.length < 4) return false;
    
    let ascending = true;
    let descending = true;
    
    for (let i = 1; i < number.length; i++) {
      const diff = parseInt(number[i]) - parseInt(number[i - 1]);
      if (diff !== 1) ascending = false;
      if (diff !== -1) descending = false;
    }
    
    return ascending || descending;
  }
  
  /**
   * Compter chiffres répétés
   */
  private countRepeatedDigits(number: string): number {
    const counts: Record<string, number> = {};
    let maxCount = 0;
    
    for (const digit of number) {
      counts[digit] = (counts[digit] || 0) + 1;
      maxCount = Math.max(maxCount, counts[digit]);
    }
    
    return maxCount;
  }
}

// Export singleton
export const callIdentificationService = new CallIdentificationService();
