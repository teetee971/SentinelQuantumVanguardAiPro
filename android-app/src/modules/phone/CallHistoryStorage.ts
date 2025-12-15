/**
 * Local Storage Service for Call History
 * 
 * Stores call events locally using AsyncStorage
 * No cloud upload - all data stays on device
 */

import AsyncStorage from '@react-native-async-storage/async-storage';

export interface StoredCallEvent {
  id: string;
  phoneNumber: string;
  timestamp: number;
  type: 'INCOMING' | 'OUTGOING' | 'MISSED' | 'BLOCKED';
  duration: number;
  action: 'ALLOWED' | 'BLOCKED' | 'FLAGGED' | 'AI_HANDLED';
  country?: string;
  riskLevel: 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  notes?: string;
}

const STORAGE_KEY = '@sentinel_call_history';
const MAX_ENTRIES = 1000; // Limit to prevent excessive storage

class CallHistoryStorage {
  
  /**
   * Save a call event
   */
  async saveCallEvent(event: Omit<StoredCallEvent, 'id'>): Promise<void> {
    try {
      const history = await this.getCallHistory();
      
      const newEvent: StoredCallEvent = {
        ...event,
        id: `call_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      };
      
      // Add to beginning of array
      history.unshift(newEvent);
      
      // Limit size
      if (history.length > MAX_ENTRIES) {
        history.splice(MAX_ENTRIES);
      }
      
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(history));
    } catch (error) {
      console.error('Failed to save call event:', error);
    }
  }
  
  /**
   * Get all call history
   */
  async getCallHistory(): Promise<StoredCallEvent[]> {
    try {
      const data = await AsyncStorage.getItem(STORAGE_KEY);
      if (data) {
        return JSON.parse(data);
      }
      return [];
    } catch (error) {
      console.error('Failed to load call history:', error);
      return [];
    }
  }
  
  /**
   * Get recent calls
   */
  async getRecentCalls(limit: number = 50): Promise<StoredCallEvent[]> {
    const history = await this.getCallHistory();
    return history.slice(0, limit);
  }
  
  /**
   * Get calls by phone number
   */
  async getCallsByNumber(phoneNumber: string): Promise<StoredCallEvent[]> {
    const history = await this.getCallHistory();
    return history.filter(call => call.phoneNumber === phoneNumber);
  }
  
  /**
   * Get blocked calls
   */
  async getBlockedCalls(): Promise<StoredCallEvent[]> {
    const history = await this.getCallHistory();
    return history.filter(call => call.action === 'BLOCKED');
  }
  
  /**
   * Get high risk calls
   */
  async getHighRiskCalls(): Promise<StoredCallEvent[]> {
    const history = await this.getCallHistory();
    return history.filter(call => 
      call.riskLevel === 'HIGH' || call.riskLevel === 'CRITICAL'
    );
  }
  
  /**
   * Update call event notes
   */
  async updateCallNotes(callId: string, notes: string): Promise<void> {
    try {
      const history = await this.getCallHistory();
      const index = history.findIndex(call => call.id === callId);
      
      if (index !== -1) {
        history[index].notes = notes;
        await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(history));
      }
    } catch (error) {
      console.error('Failed to update call notes:', error);
    }
  }
  
  /**
   * Delete call event
   */
  async deleteCallEvent(callId: string): Promise<void> {
    try {
      const history = await this.getCallHistory();
      const filtered = history.filter(call => call.id !== callId);
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(filtered));
    } catch (error) {
      console.error('Failed to delete call event:', error);
    }
  }
  
  /**
   * Clear all history
   */
  async clearHistory(): Promise<void> {
    try {
      await AsyncStorage.removeItem(STORAGE_KEY);
    } catch (error) {
      console.error('Failed to clear history:', error);
    }
  }
  
  /**
   * Get statistics
   */
  async getStatistics(): Promise<{
    total: number;
    blocked: number;
    highRisk: number;
    today: number;
  }> {
    const history = await this.getCallHistory();
    const today = new Date().setHours(0, 0, 0, 0);
    
    return {
      total: history.length,
      blocked: history.filter(c => c.action === 'BLOCKED').length,
      highRisk: history.filter(c => c.riskLevel === 'HIGH' || c.riskLevel === 'CRITICAL').length,
      today: history.filter(c => c.timestamp >= today).length,
    };
  }
}

export const callHistoryStorage = new CallHistoryStorage();
