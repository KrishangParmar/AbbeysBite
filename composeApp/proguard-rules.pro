# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.abbeysbite.app.**$$serializer { *; }
-keepclassmembers class com.abbeysbite.app.** { *** Companion; }
-keepclasseswithmembers class com.abbeysbite.app.** { kotlinx.serialization.KSerializer serializer(...); }

# RevenueCat
-keep class com.revenuecat.purchases.** { *; }

# OneSignal
-keep class com.onesignal.** { *; }

# MediaPipe / LiteRT-LM on-device Gemma — JNI + reflective class loading;
# stripping these surfaces only in a minified release build.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.genai.** { *; }
-dontwarn com.google.mediapipe.**
-keepclasseswithmembernames class * {
    native <methods>;
}
# AutoValue-generated MediaPipe option/result classes are constructed reflectively.
-keep class com.google.auto.value.** { *; }
-dontwarn com.google.auto.value.**

# Google Mobile Ads (native ad in Community for free users only)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# Ktor / OkHttp
-dontwarn okhttp3.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
