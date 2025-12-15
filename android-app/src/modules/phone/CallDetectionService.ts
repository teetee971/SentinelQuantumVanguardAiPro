/**
 * Call Detection Service
 * 
 * Listens to incoming calls and triggers alerts
 */

import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import { callIdentificationService } from './CallIdentification';
import { callHistoryStorage } from './CallHistoryStorage';

export type CallEventListener = (event: IncomingCallEvent) => void;

export interface IncomingCallEvent {
  phoneNumber: string;
  timestamp: number;
  identification: any; // CallIdentification result
  state: 'RINGING' | 'ANSWERED' | 'ENDED';
}

class CallDetectionService {
  private eventEmitter: NativeEventEmitter | null = null;
  private listeners: Map<string, CallEventListener> = new Map();
  private currentCall: IncomingCallEvent | null = null;
  
  constructor() {
    if (Platform.OS === 'android') {
      const { PhoneSecurityModule } = NativeModules;
      if (PhoneSecurityModule) {
        this.eventEmitter = new NativeEventEmitter(PhoneSecurityModule);
      }
    }
  }
  
  /**
   * Start listening for incoming calls
   */
  startListening(): void {
    if (!this.eventEmitter) {
      console.warn('CallDetectionService: No native event emitter available');
      return;
    }
    
    // Listen for incoming calls
    this.eventEmitter.addListener('onIncomingCall', (event) => {
      this.handleIncomingCall(event);
    });
    
    // Listen for call state changes
    this.eventEmitter.addListener('onCallStateChanged', (event) => {
      this.handleCallStateChange(event);
    });
    
    console.log('CallDetectionService: Started listening for calls');
  }
  
  /**
   * Stop listening
   */
  stopListening(): void {
    if (this.eventEmitter) {
      this.eventEmitter.removeAllListeners('onIncomingCall');
      this.eventEmitter.removeAllListeners('onCallStateChanged');
    }
  }
  
  /**
   * Handle incoming call
   */
  private async handleIncomingCall(event: any): Promise<void> {
    const phoneNumber = event.phoneNumber;
    const timestamp = event.timestamp;
    
    console.log('CallDetectionService: Incoming call from', phoneNumber);
    
    // Identify the caller
    const identification = callIdentificationService.identifyCall(phoneNumber);
    
    // Create call event
    const callEvent: IncomingCallEvent = {
      phoneNumber,
      timestamp,
      identification,
      state: 'RINGING',
    };
    
    this.currentCall = callEvent;
    
    // Notify all listeners
    this.listeners.forEach(listener => {
      try {
        listener(callEvent);
      } catch (error) {
        console.error('Error in call event listener:', error);
      }
    });
    
    // Save to history as incoming
    await callHistoryStorage.saveCallEvent({
      phoneNumber,
      timestamp,
      type: 'INCOMING',
      duration: 0,
      action: 'ALLOWED', // Will be updated when answered/blocked
      country: identification.country?.name,
      riskLevel: identification.riskScore.level,
    });
  }
  
  /**
   * Handle call state change
   */
  private handleCallStateChange(event: any): void {
    if (!this.currentCall) return;
    
    const state = event.state;
    console.log('CallDetectionService: Call state changed to', state);
    
    if (state === 'ANSWERED') {
      this.currentCall.state = 'ANSWERED';
    } else if (state === 'ENDED') {
      this.currentCall.state = 'ENDED';
      this.currentCall = null;
    }
  }
  
  /**
   * Add event listener
   */
  addListener(id: string, listener: CallEventListener): void {
    this.listeners.set(id, listener);
  }
  
  /**
   * Remove event listener
   */
  removeListener(id: string): void {
    this.listeners.delete(id);
  }
  
  /**
   * Get current call
   */
  getCurrentCall(): IncomingCallEvent | null {
    return this.currentCall;
  }
  
  /**
   * Block a call (save to blocked list)
   */
  async blockCall(phoneNumber: string): Promise<void> {
    // Save to blocked list in storage
    await callHistoryStorage.saveCallEvent({
      phoneNumber,
      timestamp: Date.now(),
      type: 'BLOCKED',
      duration: 0,
      action: 'BLOCKED',
      riskLevel: 'HIGH',
    });
    
    console.log('CallDetectionService: Blocked number', phoneNumber);
  }
  
  /**
   * Flag a call as spam
   */
  async flagCall(phoneNumber: string, notes?: string): Promise<void> {
    await callHistoryStorage.saveCallEvent({
      phoneNumber,
      timestamp: Date.now(),
      type: 'INCOMING',
      duration: 0,
      action: 'FLAGGED',
      riskLevel: 'HIGH',
      notes: notes || 'Flagged as spam by user',
    });
    
    console.log('CallDetectionService: Flagged number', phoneNumber);
  }
}

export const callDetectionService = new CallDetectionService();
