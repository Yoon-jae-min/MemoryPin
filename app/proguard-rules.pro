# 기본 ProGuard 규칙
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Android 컴포넌트 (매니페스트에서 참조)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# ViewBinding
-keep class com.memorypin.databinding.** { *; }

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
