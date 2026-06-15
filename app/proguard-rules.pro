# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve annotations, signatures, and line numbers of stack traces for debugging
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Keep our BuildConfig so that our dynamic endpoint URLs and secret keys are available
-keep class com.example.BuildConfig { *; }

# Keep our Moshi model classes and generated adapters from being stripped or broken
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class *JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Keep our database entities and repository data structures
-keep class com.example.data.** { *; }
-keep class com.example.domain.** { *; }

# Retrofit keep rules
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# OkHttp/Okio keep rules
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
