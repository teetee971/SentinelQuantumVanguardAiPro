# Project-specific R8 rules.
# AndroidX, Compose, OkHttp and Kotlin publish their own consumer rules.

-keepattributes *Annotation*, InnerClasses

# ROME may discover parser implementations reflectively.
-keep class com.rometools.** { *; }

# libphonenumber loads its country metadata by resource name.
-keep class com.google.i18n.phonenumbers.** { *; }
-dontwarn com.google.i18n.phonenumbers.**
