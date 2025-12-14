# Sentinel Quantum Vanguard AI Pro - ProGuard Rules
# Keep WebView JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep model classes
-keep class com.sentinel.quantumvanguard.model.** { *; }

# Keep MainActivity and fragments
-keep class com.sentinel.quantumvanguard.MainActivity { *; }
-keep class com.sentinel.quantumvanguard.** extends androidx.fragment.app.Fragment { *; }
