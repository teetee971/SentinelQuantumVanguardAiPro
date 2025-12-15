package com.sentinel.phonemodule;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import androidx.core.app.ActivityCompat;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Native Android module for Phone Security features
 * 
 * Provides secure access to:
 * - Call log
 * - Contacts
 * - Phone state
 * 
 * All functions require proper Android permissions
 * No spyware functionality
 * Compliant with Google Play policies
 */
public class PhoneSecurityModule extends ReactContextBaseJavaModule {
    
    private static final String MODULE_NAME = "PhoneSecurityModule";
    private final ReactApplicationContext reactContext;
    
    public PhoneSecurityModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }
    
    @Override
    public String getName() {
        return MODULE_NAME;
    }
    
    /**
     * Get call log entries
     * Requires READ_CALL_LOG permission
     */
    @ReactMethod
    public void getCallLog(int limit, Promise promise) {
        if (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.READ_CALL_LOG) 
            != PackageManager.PERMISSION_GRANTED) {
            promise.reject("PERMISSION_DENIED", "READ_CALL_LOG permission not granted");
            return;
        }
        
        try {
            WritableArray callLogArray = new WritableNativeArray();
            
            String[] projection = new String[] {
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME
            };
            
            Cursor cursor = reactContext.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC LIMIT " + limit
            );
            
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < limit) {
                    WritableMap callEntry = new WritableNativeMap();
                    
                    callEntry.putString("id", cursor.getString(0));
                    callEntry.putString("number", cursor.getString(1) != null ? cursor.getString(1) : "Unknown");
                    
                    int callType = cursor.getInt(2);
                    String type = "UNKNOWN";
                    switch (callType) {
                        case CallLog.Calls.INCOMING_TYPE:
                            type = "INCOMING";
                            break;
                        case CallLog.Calls.OUTGOING_TYPE:
                            type = "OUTGOING";
                            break;
                        case CallLog.Calls.MISSED_TYPE:
                            type = "MISSED";
                            break;
                        case CallLog.Calls.REJECTED_TYPE:
                            type = "REJECTED";
                            break;
                        case CallLog.Calls.BLOCKED_TYPE:
                            type = "BLOCKED";
                            break;
                    }
                    callEntry.putString("type", type);
                    callEntry.putDouble("date", cursor.getLong(3));
                    callEntry.putInt("duration", cursor.getInt(4));
                    
                    String name = cursor.getString(5);
                    if (name != null) {
                        callEntry.putString("name", name);
                    }
                    
                    callLogArray.pushMap(callEntry);
                    count++;
                }
                cursor.close();
            }
            
            promise.resolve(callLogArray);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to read call log: " + e.getMessage());
        }
    }
    
    /**
     * Get contacts
     * Requires READ_CONTACTS permission
     */
    @ReactMethod
    public void getContacts(int limit, Promise promise) {
        if (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            promise.reject("PERMISSION_DENIED", "READ_CONTACTS permission not granted");
            return;
        }
        
        try {
            WritableArray contactsArray = new WritableNativeArray();
            
            String[] projection = new String[] {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            };
            
            Cursor cursor = reactContext.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC LIMIT " + limit
            );
            
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < limit) {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    int hasPhone = cursor.getInt(2);
                    
                    if (hasPhone > 0) {
                        WritableMap contact = new WritableNativeMap();
                        contact.putString("id", id);
                        contact.putString("name", name != null ? name : "Unknown");
                        
                        // Get phone numbers for this contact
                        WritableArray phoneNumbers = new WritableNativeArray();
                        Cursor phoneCursor = reactContext.getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id},
                            null
                        );
                        
                        if (phoneCursor != null) {
                            while (phoneCursor.moveToNext()) {
                                String phoneNumber = phoneCursor.getString(
                                    phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                );
                                if (phoneNumber != null) {
                                    phoneNumbers.pushString(phoneNumber);
                                }
                            }
                            phoneCursor.close();
                        }
                        
                        contact.putArray("phoneNumbers", phoneNumbers);
                        contactsArray.pushMap(contact);
                        count++;
                    }
                }
                cursor.close();
            }
            
            promise.resolve(contactsArray);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to read contacts: " + e.getMessage());
        }
    }
    
    /**
     * Get phone state information
     * Requires READ_PHONE_STATE permission
     */
    @ReactMethod
    public void getPhoneState(Promise promise) {
        if (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.READ_PHONE_STATE) 
            != PackageManager.PERMISSION_GRANTED) {
            promise.reject("PERMISSION_DENIED", "READ_PHONE_STATE permission not granted");
            return;
        }
        
        try {
            TelephonyManager telephonyManager = (TelephonyManager) reactContext.getSystemService(
                ReactApplicationContext.TELEPHONY_SERVICE
            );
            
            WritableMap phoneState = new WritableNativeMap();
            
            // Get network operator name
            String networkOperator = telephonyManager.getNetworkOperatorName();
            if (networkOperator != null && !networkOperator.isEmpty()) {
                phoneState.putString("networkOperator", networkOperator);
            }
            
            // Get SIM operator name
            String simOperator = telephonyManager.getSimOperatorName();
            if (simOperator != null && !simOperator.isEmpty()) {
                phoneState.putString("simOperator", simOperator);
            }
            
            // Get network country ISO
            String networkCountry = telephonyManager.getNetworkCountryIso();
            if (networkCountry != null && !networkCountry.isEmpty()) {
                phoneState.putString("networkCountry", networkCountry.toUpperCase());
            }
            
            // Get phone type
            int phoneType = telephonyManager.getPhoneType();
            String phoneTypeStr = "UNKNOWN";
            switch (phoneType) {
                case TelephonyManager.PHONE_TYPE_GSM:
                    phoneTypeStr = "GSM";
                    break;
                case TelephonyManager.PHONE_TYPE_CDMA:
                    phoneTypeStr = "CDMA";
                    break;
                case TelephonyManager.PHONE_TYPE_SIP:
                    phoneTypeStr = "SIP";
                    break;
            }
            phoneState.putString("phoneType", phoneTypeStr);
            
            promise.resolve(phoneState);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get phone state: " + e.getMessage());
        }
    }
    
    /**
     * Check if a permission is granted
     */
    @ReactMethod
    public void hasPermission(String permission, Promise promise) {
        try {
            boolean granted = ActivityCompat.checkSelfPermission(reactContext, permission) 
                == PackageManager.PERMISSION_GRANTED;
            promise.resolve(granted);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to check permission: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("PERMISSION_READ_CALL_LOG", Manifest.permission.READ_CALL_LOG);
        constants.put("PERMISSION_READ_CONTACTS", Manifest.permission.READ_CONTACTS);
        constants.put("PERMISSION_READ_PHONE_STATE", Manifest.permission.READ_PHONE_STATE);
        constants.put("PERMISSION_READ_SMS", Manifest.permission.READ_SMS);
        return constants;
    }
}
