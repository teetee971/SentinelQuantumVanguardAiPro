#!/usr/bin/env node

/**
 * Monthly Usage Reset Script
 * 
 * This script resets usage counters for all users on the 1st of each month.
 * It's designed to be run by GitHub Actions scheduler or can be run manually.
 * 
 * Usage:
 *   node scripts/reset-usage.js
 * 
 * Environment variables:
 *   API_URL - Base URL of the API (default: http://localhost:3333)
 *   API_KEY - API key for authentication
 */

const https = require('https');
const http = require('http');

const API_URL = process.env.API_URL || 'http://localhost:3333';
const API_KEY = process.env.API_KEY || '';

console.log('🔄 Starting monthly usage reset...');
console.log(`📍 API URL: ${API_URL}`);
console.log(`📅 Reset Date: ${new Date().toISOString()}`);

/**
 * Make HTTP request
 */
function makeRequest(url, method = 'GET', data = null) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    const client = urlObj.protocol === 'https:' ? https : http;
    
    const options = {
      hostname: urlObj.hostname,
      port: urlObj.port,
      path: urlObj.pathname + urlObj.search,
      method: method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${API_KEY}`,
      },
    };

    if (data) {
      const body = JSON.stringify(data);
      options.headers['Content-Length'] = Buffer.byteLength(body);
    }

    const req = client.request(options, (res) => {
      let responseData = '';

      res.on('data', (chunk) => {
        responseData += chunk;
      });

      res.on('end', () => {
        try {
          const parsed = JSON.parse(responseData);
          resolve({ status: res.statusCode, data: parsed });
        } catch (e) {
          resolve({ status: res.statusCode, data: responseData });
        }
      });
    });

    req.on('error', (error) => {
      reject(error);
    });

    if (data) {
      req.write(JSON.stringify(data));
    }

    req.end();
  });
}

/**
 * Fetch all users (mock for MVP)
 * In production, this would query the database
 */
async function getAllUsers() {
  // TODO: Replace with actual database query
  // For MVP, return a mock list
  return [
    { id: 'user-1', email: 'user1@example.com' },
    { id: 'user-2', email: 'user2@example.com' },
    { id: 'user-3', email: 'user3@example.com' },
  ];
}

/**
 * Reset usage for a single user
 */
async function resetUserUsage(userId) {
  try {
    const response = await makeRequest(
      `${API_URL}/api/usage/${userId}/reset`,
      'POST'
    );
    
    if (response.status >= 200 && response.status < 300) {
      console.log(`✅ Reset successful for user: ${userId}`);
      return { success: true, userId };
    } else {
      console.error(`❌ Reset failed for user: ${userId} - Status: ${response.status}`);
      return { success: false, userId, error: response.data };
    }
  } catch (error) {
    console.error(`❌ Reset error for user: ${userId}`, error.message);
    return { success: false, userId, error: error.message };
  }
}

/**
 * Main function
 */
async function main() {
  try {
    // Get all users
    console.log('📋 Fetching users...');
    const users = await getAllUsers();
    console.log(`👥 Found ${users.length} users to reset`);

    // Reset usage for each user
    const results = [];
    for (const user of users) {
      const result = await resetUserUsage(user.id);
      results.push(result);
      
      // Add small delay to avoid overwhelming the API
      await new Promise(resolve => setTimeout(resolve, 100));
    }

    // Summary
    const successful = results.filter(r => r.success).length;
    const failed = results.filter(r => !r.success).length;

    console.log('\n📊 Reset Summary:');
    console.log(`   ✅ Successful: ${successful}`);
    console.log(`   ❌ Failed: ${failed}`);
    console.log(`   📈 Total: ${results.length}`);

    // Export summary for monitoring
    const summary = {
      date: new Date().toISOString(),
      total: results.length,
      successful,
      failed,
      results,
    };

    console.log('\n📄 Summary JSON:');
    console.log(JSON.stringify(summary, null, 2));

    // Exit with error code if any resets failed
    if (failed > 0) {
      console.error('\n⚠️  Some resets failed. Please investigate.');
      process.exit(1);
    }

    console.log('\n✅ Monthly usage reset completed successfully!');
    process.exit(0);
  } catch (error) {
    console.error('\n❌ Fatal error during usage reset:', error);
    process.exit(1);
  }
}

// Run the script
main();
