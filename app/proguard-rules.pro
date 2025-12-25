# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.autoglm.app.core.** { *; }

# Keep Shizuku classes
-keep class com.autoglm.app.shizuku.** { *; }
-keep class com.autoglm.app.IUserService { *; }
-keep class com.autoglm.app.IUserService$Stub { *; }
