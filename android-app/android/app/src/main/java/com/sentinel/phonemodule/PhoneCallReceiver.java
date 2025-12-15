package com.sentinel.phonemodule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

/**
 * Détection d'appels entrants en temps réel
 * 
 * Envoie un événement à React Native quand un appel arrive
 * IMPORTANT: Respecte les lois sur la vie privée
 */
public class PhoneCallReceiver extends BroadcastReceiver {
    
    private static final String TAG = "PhoneCallReceiver";
    private static ReactApplicationContext reactContext;
    
    public static void setReactContext(ReactApplicationContext context) {
        reactContext = context;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            
            if (state == null) return;
            
            if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                // Appel entrant détecté
                handleIncomingCall(incomingNumber != null ? incomingNumber : "Unknown");
            } else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                // Appel décroché
                handleCallAnswered();
            } else if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                // Appel terminé
                handleCallEnded();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in PhoneCallReceiver", e);
        }
    }
    
    private void handleIncomingCall(String phoneNumber) {
        Log.i(TAG, "Incoming call detected: " + phoneNumber);
        
        if (reactContext != null) {
            WritableMap params = new WritableNativeMap();
            params.putString("phoneNumber", phoneNumber);
            params.putString("state", "RINGING");
            params.putDouble("timestamp", System.currentTimeMillis());
            
            sendEvent("onIncomingCall", params);
        }
    }
    
    private void handleCallAnswered() {
        Log.i(TAG, "Call answered");
        
        if (reactContext != null) {
            WritableMap params = new WritableNativeMap();
            params.putString("state", "ANSWERED");
            params.putDouble("timestamp", System.currentTimeMillis());
            
            sendEvent("onCallStateChanged", params);
        }
    }
    
    private void handleCallEnded() {
        Log.i(TAG, "Call ended");
        
        if (reactContext != null) {
            WritableMap params = new WritableNativeMap();
            params.putString("state", "ENDED");
            params.putDouble("timestamp", System.currentTimeMillis());
            
            sendEvent("onCallStateChanged", params);
        }
    }
    
    private void sendEvent(String eventName, WritableMap params) {
        if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        }
    }
}
