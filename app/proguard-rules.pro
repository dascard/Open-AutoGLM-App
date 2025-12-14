# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.autoglm.app.core.** { *; }
