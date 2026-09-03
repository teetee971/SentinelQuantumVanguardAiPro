# Project-specific R8 rules.
# AndroidX, Compose, OkHttp and Kotlin publish their own consumer rules.

-keepattributes *Annotation*, InnerClasses

# ROME may discover parser implementations reflectively.
-keep class com.rometools.** { *; }
