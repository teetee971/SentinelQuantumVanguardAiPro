# Regles de conservation de securite pour Sentinel Quantum Vanguard

-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * {*;}
-keep @androidx.room.Entity class * {*;}
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-dontwarn androidx.room.**

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.rometools.rome.** { *; }
-dontwarn com.rometools.rome.**

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn android.ext.**
-dontwarn androidx.ext.**
-dontwarn org.slf4j.**

-keep class org.json.** { *; }
-dontwarn org.json.**
