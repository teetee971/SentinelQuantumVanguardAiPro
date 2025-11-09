/**
 * Anti-Fraud Anomaly Detection Script
 * 
 * Uses Isolation Forest algorithm for detecting anomalous behavior patterns
 * in user activity logs. Intended for weekly batch processing.
 * 
 * Usage:
 *   npx tsx scripts/anti-fraud/isolation-forest.ts [--input data.json] [--output results.json]
 */

import * as fs from 'fs';
import * as path from 'path';
// @ts-ignore - ml-isolation-forest may not have types
import IsolationForest from 'ml-isolation-forest';
import { mean, standardDeviation } from 'simple-statistics';

interface UserActivity {
  userId: string;
  timestamp: number;
  action: string;
  ipAddress?: string;
  userAgent?: string;
  sessionDuration?: number;
  requestCount?: number;
  errorRate?: number;
}

interface AnomalyResult {
  userId: string;
  anomalyScore: number;
  isAnomaly: boolean;
  features: number[];
  timestamp: number;
}

/**
 * Extract numerical features from user activity for anomaly detection
 */
function extractFeatures(activity: UserActivity): number[] {
  return [
    activity.sessionDuration || 0,
    activity.requestCount || 0,
    activity.errorRate || 0,
    // Add more features as needed
  ];
}

/**
 * Normalize features using z-score normalization
 */
function normalizeFeatures(data: number[][]): number[][] {
  if (data.length === 0) return data;
  
  const numFeatures = data[0].length;
  const normalized: number[][] = [];

  for (let i = 0; i < numFeatures; i++) {
    const column = data.map(row => row[i]);
    const columnMean = mean(column);
    const columnStd = standardDeviation(column);

    if (columnStd === 0) continue;

    for (let j = 0; j < data.length; j++) {
      if (!normalized[j]) normalized[j] = [];
      normalized[j][i] = (data[j][i] - columnMean) / columnStd;
    }
  }

  return normalized;
}

/**
 * Run Isolation Forest anomaly detection
 */
async function detectAnomalies(
  activities: UserActivity[],
  options: { contamination?: number; nTrees?: number } = {}
): Promise<AnomalyResult[]> {
  const { contamination = 0.1, nTrees = 100 } = options;

  console.log(`Processing ${activities.length} user activities...`);

  // Extract and normalize features
  const features = activities.map(extractFeatures);
  const normalizedFeatures = normalizeFeatures(features);

  // Train Isolation Forest
  console.log('Training Isolation Forest model...');
  const iforest = new IsolationForest({
    nEstimators: nTrees,
    maxSamples: Math.min(256, activities.length),
    contamination,
  });

  iforest.fit(normalizedFeatures);

  // Predict anomalies
  console.log('Detecting anomalies...');
  const predictions = iforest.predict(normalizedFeatures);
  const scores = iforest.score(normalizedFeatures);

  // Build results
  const results: AnomalyResult[] = activities.map((activity, i) => ({
    userId: activity.userId,
    anomalyScore: scores[i],
    isAnomaly: predictions[i] === -1,
    features: features[i],
    timestamp: activity.timestamp,
  }));

  const anomalyCount = results.filter(r => r.isAnomaly).length;
  console.log(`Detected ${anomalyCount} anomalies (${(anomalyCount / results.length * 100).toFixed(2)}%)`);

  return results;
}

/**
 * Main execution function
 */
async function main() {
  const args = process.argv.slice(2);
  const inputFile = args.find(arg => arg.startsWith('--input='))?.split('=')[1] || 'data/user-activity.json';
  const outputFile = args.find(arg => arg.startsWith('--output='))?.split('=')[1] || 'data/anomaly-results.json';

  console.log('Anti-Fraud Anomaly Detection');
  console.log('============================');
  console.log(`Input file: ${inputFile}`);
  console.log(`Output file: ${outputFile}`);

  // Check if input file exists
  if (!fs.existsSync(inputFile)) {
    console.error(`Error: Input file not found: ${inputFile}`);
    console.log('\nCreating sample data file for testing...');
    
    // Create sample data
    const sampleData: UserActivity[] = Array.from({ length: 100 }, (_, i) => ({
      userId: `user-${i}`,
      timestamp: Date.now() - (i * 60000),
      action: 'login',
      sessionDuration: Math.random() * 3600,
      requestCount: Math.floor(Math.random() * 100),
      errorRate: Math.random() * 0.1,
    }));

    // Add some anomalies
    sampleData.push({
      userId: 'user-anomaly-1',
      timestamp: Date.now(),
      action: 'login',
      sessionDuration: 10000, // Unusually long
      requestCount: 1000, // Unusually high
      errorRate: 0.5, // High error rate
    });

    fs.mkdirSync(path.dirname(inputFile), { recursive: true });
    fs.writeFileSync(inputFile, JSON.stringify(sampleData, null, 2));
    console.log(`Sample data created at ${inputFile}`);
  }

  // Load data
  const rawData = fs.readFileSync(inputFile, 'utf-8');
  const activities: UserActivity[] = JSON.parse(rawData);

  // Run anomaly detection
  const results = await detectAnomalies(activities);

  // Save results
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  fs.writeFileSync(outputFile, JSON.stringify(results, null, 2));
  console.log(`\nResults saved to ${outputFile}`);

  // Print summary
  const anomalies = results.filter(r => r.isAnomaly);
  console.log('\nTop 5 Anomalies:');
  anomalies
    .sort((a, b) => b.anomalyScore - a.anomalyScore)
    .slice(0, 5)
    .forEach((anomaly, i) => {
      console.log(`${i + 1}. User: ${anomaly.userId}, Score: ${anomaly.anomalyScore.toFixed(4)}`);
    });
}

// Run if called directly
if (require.main === module) {
  main().catch(console.error);
}

export { detectAnomalies, extractFeatures, normalizeFeatures };
