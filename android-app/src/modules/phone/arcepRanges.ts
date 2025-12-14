/**
 * ARCEP (France) - Plages de numéros réservées au démarchage téléphonique
 * 
 * Source: ARCEP (Autorité de régulation des communications électroniques et des postes)
 * Mise à jour: Décembre 2024
 * 
 * IMPORTANT:
 * - Base locale statique (pas d'API, pas de cloud)
 * - Aucune donnée personnelle collectée
 * - Utilisé comme facteur pondéré du ThreatScore uniquement
 * - Pas de verdict automatique
 * - Google Play compliant
 * 
 * DISCLAIMER:
 * Ces plages sont officiellement réservées au démarchage commercial en France.
 * Un appel depuis ces numéros N'EST PAS forcément illégitime, mais indique
 * une probabilité élevée d'être du démarchage commercial.
 */

/**
 * Plages de numéros 09 réservées au démarchage (ARCEP France)
 * Format: préfixes de numéros (09XXXXXXXX)
 */
const ARCEP_09_RANGES = [
  '0162', '0163', '0270', '0271', '0377', '0378', '0424', '0425',
  '0568', '0569', '0948', '0949',
];

/**
 * Vérification si un numéro français est dans les plages ARCEP démarchage
 * 
 * @param phoneNumber - Numéro de téléphone (format: +33XXXXXXXXX ou 0XXXXXXXXX)
 * @returns true si le numéro est dans une plage ARCEP démarchage
 * 
 * @example
 * isArcepDemarchage('+33162345678') // true
 * isArcepDemarchage('0162345678')   // true
 * isArcepDemarchage('0601234567')   // false
 */
export function isArcepDemarchage(phoneNumber: string): boolean {
  if (!phoneNumber) return false;
  
  // Normaliser le numéro (enlever espaces, tirets, points)
  let normalized = phoneNumber.replace(/[\s\-\.]/g, '');
  
  // Convertir format international (+33) en format national (0)
  if (normalized.indexOf('+33') === 0) {
    normalized = '0' + normalized.substring(3);
  } else if (normalized.indexOf('0033') === 0) {
    normalized = '0' + normalized.substring(4);
  }
  
  // Vérifier si le numéro est français et a la bonne longueur
  if (normalized.indexOf('0') !== 0 || normalized.length !== 10) {
    return false; // Pas un numéro français valide
  }
  
  // Extraire les 4 premiers chiffres
  const prefix = normalized.substring(0, 4);
  
  // Vérifier dans les plages 09
  return ARCEP_09_RANGES.indexOf(prefix) !== -1;
}

/**
 * Obtenir des informations détaillées sur un numéro ARCEP
 * 
 * @param phoneNumber - Numéro de téléphone
 * @returns Informations ARCEP ou null si pas concerné
 */
export interface ArcepInfo {
  isArcepRange: boolean;
  prefix: string;
  category: 'TELEMARKETING' | 'NONE';
  confidence: number;      // 0-100%
  explanation: string;     // Explication en français
}

export function getArcepInfo(phoneNumber: string): ArcepInfo {
  if (!phoneNumber) {
    return {
      isArcepRange: false,
      prefix: '',
      category: 'NONE',
      confidence: 0,
      explanation: 'Numéro invalide',
    };
  }
  
  const isArcep = isArcepDemarchage(phoneNumber);
  
  if (isArcep) {
    // Normaliser pour extraire le préfixe
    let normalized = phoneNumber.replace(/[\s\-\.]/g, '');
    if (normalized.indexOf('+33') === 0) {
      normalized = '0' + normalized.substring(3);
    } else if (normalized.indexOf('0033') === 0) {
      normalized = '0' + normalized.substring(4);
    }
    const prefix = normalized.substring(0, 4);
    
    return {
      isArcepRange: true,
      prefix,
      category: 'TELEMARKETING',
      confidence: 90, // 90% confiance que c'est du démarchage
      explanation: `Numéro ${prefix}XX officiellemnt réservé au démarchage commercial (ARCEP France). Probablement un appel commercial légitime mais non sollicité.`,
    };
  }
  
  return {
    isArcepRange: false,
    prefix: '',
    category: 'NONE',
    confidence: 0,
    explanation: 'Numéro hors plages ARCEP démarchage',
  };
}

/**
 * Calculer le facteur ARCEP pour le ThreatScore
 * 
 * Points ajoutés au ThreatScore si numéro ARCEP:
 * - +15 points si dans plage ARCEP démarchage
 * - +0 points sinon
 * 
 * Ce facteur est pondéré et ne constitue PAS un verdict unique.
 * Un appel depuis une plage ARCEP peut être légitime.
 * 
 * @param phoneNumber - Numéro de téléphone
 * @returns Points à ajouter au ThreatScore (0-20)
 */
export function calculateArcepFactor(phoneNumber: string): number {
  const arcepInfo = getArcepInfo(phoneNumber);
  
  if (arcepInfo.isArcepRange) {
    // +15 points si dans plage ARCEP (sur maximum de 20 pour ce facteur)
    // Pas 20/20 car même numéro ARCEP peut être appel légitime
    return 15;
  }
  
  return 0; // Pas dans plage ARCEP = +0 points
}

/**
 * Obtenir une explication du facteur ARCEP pour l'utilisateur
 * 
 * @param phoneNumber - Numéro de téléphone
 * @returns Explication en français ou null si pas concerné
 */
export function getArcepExplanation(phoneNumber: string): string | null {
  const arcepInfo = getArcepInfo(phoneNumber);
  
  if (arcepInfo.isArcepRange) {
    return `Ce numéro (${arcepInfo.prefix}XX) provient d'une plage officiellement réservée au démarchage commercial par l'ARCEP (autorité française des télécoms). Bien que l'appel puisse être légitime, il s'agit probablement d'un démarchage commercial.`;
  }
  
  return null;
}

/**
 * DISCLAIMER LEGAL (à afficher dans l'UI)
 */
export const ARCEP_DISCLAIMER = `
Les plages de numéros ARCEP sont fournies à titre informatif uniquement.
Un numéro dans une plage ARCEP démarchage N'EST PAS forcément malveillant.
Il s'agit d'appels commerciaux légitimes mais potentiellement non sollicités.
L'utilisateur reste seul responsable de ses décisions (bloquer/répondre).
Données basées sur les allocations ARCEP publiques (France).
`.trim();

/**
 * Statistiques ARCEP (pour documentation/tests)
 */
export const ARCEP_STATS = {
  totalRanges: ARCEP_09_RANGES.length,
  category: 'Démarchage commercial (09)',
  coverage: '~12 plages 09XX',
  lastUpdate: '2024-12',
  source: 'ARCEP France (données publiques)',
};
