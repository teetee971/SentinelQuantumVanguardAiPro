/**
 * Native Phone Security Module Bridge
 * 
 * TypeScript wrapper for the native Android PhoneSecurityModule
 */

import { NativeModules, Platform } from 'react-native';

interface PhoneSecurityModuleInterface {
  PERMISSION_READ_CALL_LOG: string;
  PERMISSION_READ_CONTACTS: string;
  PERMISSION_READ_PHONE_STATE: string;
  PERMISSION_READ_SMS: string;
  
  getCallLog(limit: number): Promise<CallLogEntry[]>;
  getContacts(limit: number): Promise<Contact[]>;
  getPhoneState(): Promise<PhoneState>;
  hasPermission(permission: string): Promise<boolean>;
}

export interface CallLogEntry {
  id: string;
  number: string;
  type: 'INCOMING' | 'OUTGOING' | 'MISSED' | 'REJECTED' | 'BLOCKED' | 'UNKNOWN';
  date: number;
  duration: number;
  name?: string;
}

export interface Contact {
  id: string;
  name: string;
  phoneNumbers: string[];
}

export interface PhoneState {
  networkOperator?: string;
  simOperator?: string;
  networkCountry?: string;
  phoneType?: 'GSM' | 'CDMA' | 'SIP' | 'UNKNOWN';
}

// Get the native module
const LINKING_ERROR =
  `The package 'PhoneSecurityModule' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const PhoneSecurityModuleNative: PhoneSecurityModuleInterface = NativeModules.PhoneSecurityModule
  ? NativeModules.PhoneSecurityModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

/**
 * Phone Security Module
 * Provides access to phone features with proper permission handling
 */
export class NativePhoneSecurityModule {
  
  /**
   * Get call log entries
   */
  async getCallLog(limit: number = 100): Promise<CallLogEntry[]> {
    if (Platform.OS !== 'android') {
      throw new Error('PhoneSecurityModule is only available on Android');
    }
    
    try {
      const callLog = await PhoneSecurityModuleNative.getCallLog(limit);
      return callLog;
    } catch (error: any) {
      if (error.code === 'PERMISSION_DENIED') {
        throw new Error('READ_CALL_LOG permission not granted');
      }
      throw error;
    }
  }
  
  /**
   * Get contacts
   */
  async getContacts(limit: number = 500): Promise<Contact[]> {
    if (Platform.OS !== 'android') {
      throw new Error('PhoneSecurityModule is only available on Android');
    }
    
    try {
      const contacts = await PhoneSecurityModuleNative.getContacts(limit);
      return contacts;
    } catch (error: any) {
      if (error.code === 'PERMISSION_DENIED') {
        throw new Error('READ_CONTACTS permission not granted');
      }
      throw error;
    }
  }
  
  /**
   * Get phone state information
   */
  async getPhoneState(): Promise<PhoneState> {
    if (Platform.OS !== 'android') {
      throw new Error('PhoneSecurityModule is only available on Android');
    }
    
    try {
      const state = await PhoneSecurityModuleNative.getPhoneState();
      return state;
    } catch (error: any) {
      if (error.code === 'PERMISSION_DENIED') {
        throw new Error('READ_PHONE_STATE permission not granted');
      }
      throw error;
    }
  }
  
  /**
   * Check if a permission is granted
   */
  async hasPermission(permission: string): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return false;
    }
    
    try {
      return await PhoneSecurityModuleNative.hasPermission(permission);
    } catch (error) {
      console.error('Error checking permission:', error);
      return false;
    }
  }
  
  /**
   * Get permission constants
   */
  get PERMISSIONS() {
    return {
      READ_CALL_LOG: PhoneSecurityModuleNative.PERMISSION_READ_CALL_LOG || 'android.permission.READ_CALL_LOG',
      READ_CONTACTS: PhoneSecurityModuleNative.PERMISSION_READ_CONTACTS || 'android.permission.READ_CONTACTS',
      READ_PHONE_STATE: PhoneSecurityModuleNative.PERMISSION_READ_PHONE_STATE || 'android.permission.READ_PHONE_STATE',
      READ_SMS: PhoneSecurityModuleNative.PERMISSION_READ_SMS || 'android.permission.READ_SMS',
    };
  }
}

export const nativePhoneModule = new NativePhoneSecurityModule();
