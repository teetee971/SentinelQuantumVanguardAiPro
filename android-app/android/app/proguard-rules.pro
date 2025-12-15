# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# ========================================
# REACT NATIVE CORE
# ========================================

# React Native
-keep,allowobfuscation @interface com.facebook.proguard.annotations.DoNotStrip
-keep,allowobfuscation @interface com.facebook.proguard.annotations.KeepGettersAndSetters
-keep,allowobfuscation @interface com.facebook.common.internal.DoNotStrip
-keep @com.facebook.proguard.annotations.DoNotStrip class *
-keep @com.facebook.common.internal.DoNotStrip class *
-keepclassmembers class * {
    @com.facebook.proguard.annotations.DoNotStrip *;
    @com.facebook.common.internal.DoNotStrip *;
}

-keepclassmembers @com.facebook.proguard.annotations.KeepGettersAndSetters class * {
  void set*(***);
  *** get*();
}

# ========================================
# SECURITY & ANTI-TAMPERING (PRODUCTION)
# ========================================

# Remove all logging in production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Obfuscate package names and class names
-repackageclasses 'sentinel.obf'

# Advanced obfuscation - rename all non-public classes/methods
-allowaccessmodification

# Make the code harder to reverse engineer
-overloadaggressively

# Optimize bytecode
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# Remove debug attributes for anti-tampering
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ========================================
# APPLICATION SECURITY
# ========================================

# Keep BuildConfig for signature verification
-keep class **.BuildConfig { *; }

# Keep main application class
-keep class com.sentinel.MainApplication { *; }
-keep class com.sentinel.MainActivity { *; }

# Keep phone module (critical security component)
-keep class com.sentinel.phonemodule.** { *; }

# Keep native methods (JNI)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ========================================
# REACT NATIVE COMPATIBILITY
# ========================================

# Keep JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep React Native modules
-keepclassmembers class * extends com.facebook.react.bridge.ReactContextBaseJavaModule {
    public <methods>;
}

# Keep React Native view managers
-keep class * extends com.facebook.react.uimanager.ViewManager {
    <init>(...);
    public <methods>;
}

# Keep React Native packages
-keep class * implements com.facebook.react.bridge.ReactPackage {
    public <methods>;
}

# ========================================
# GENERAL ANDROID
# ========================================

# Keep annotations
-keepattributes *Annotation*

# Keep runtime visible annotations
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations

# Keep broadcast receivers
-keep public class * extends android.content.BroadcastReceiver

# Keep services
-keep public class * extends android.app.Service

# Keep activities
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity

# Preserve the special static methods that are required in all enumeration classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================================
# INSTITUTIONAL COMPLIANCE
# ========================================

# For government/defense use cases
# Remove all crash reporting and analytics
# Note: (...) is standard ProGuard syntax for matching any parameters
-assumenosideeffects class com.facebook.react.modules.core.ExceptionsManagerModule {
    public void reportException(...);
}

# Suppress warnings for common libraries
-dontwarn com.facebook.react.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
