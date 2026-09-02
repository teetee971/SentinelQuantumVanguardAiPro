/**
 * Local Storage Service for Call History
 *
 * Stores call events locally using AsyncStorage
 * No cloud upload - all data stays on device
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { parseStoredCallHistory } from './CallHistoryParser';

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
const MAX_ENTRIES = 1000;

class CallHistoryStorage {
  async saveCallEvent(event: Omit<StoredCallEvent, 'id'>): Promise<void> {
    try {
      const history = await this.getCallHistory();
      const newEvent: StoredCallEvent = {
        ...event,
        id: `call_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      };
      history.unshift(newEvent);
      if (history.length > MAX_ENTRIES) history.splice(MAX_ENTRIES);
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(history));
    } catch (error) {
      console.error('Failed to save call event:', error);
    }
  }

  async getCallHistory(): Promise<StoredCallEvent[]> {
    try {
      const data = await AsyncStorage.getItem(STORAGE_KEY);
      return data ? parseStoredCallHistory(data) : [];
    } catch (error) {
      console.error('Failed to load call history:', error);
      return [];
    }
  }

  async getRecentCalls(limit: number = 50): Promise<StoredCallEvent[]> {
    const history = await this.getCallHistory();
    return history.slice(0, Math.max(0, limit));
  }

  async getCallsByNumber(phoneNumber: string): Promise<StoredCallEvent[]> {
    return (await this.getCallHistory()).filter(call => call.phoneNumber === phoneNumber);
  }

  async getBlockedCalls(): Promise<StoredCallEvent[]> {
    return (await this.getCallHistory()).filter(call => call.action === 'BLOCKED');
  }

  async getHighRiskCalls(): Promise<StoredCallEvent[]> {
    return (await this.getCallHistory()).filter(call =>
      call.riskLevel === 'HIGH' || call.riskLevel === 'CRITICAL'
    );
  }

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

  async deleteCallEvent(callId: string): Promise<void> {
    try {
      const history = await this.getCallHistory();
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(history.filter(call => call.id !== callId)));
    } catch (error) {
      console.error('Failed to delete call event:', error);
    }
  }

  async clearHistory(): Promise<void> {
    try {
      await AsyncStorage.removeItem(STORAGE_KEY);
    } catch (error) {
      console.error('Failed to clear history:', error);
    }
  }

  async getStatistics(): Promise<{ total: number; blocked: number; highRisk: number; today: number }> {
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
