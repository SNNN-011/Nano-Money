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

# Keep only Retrofit API service interfaces so that Retrofit dynamic proxying is not broken by name obfuscation, while allowing all other UI/ViewModel/Security classes to be fully obfuscated and optimized
-keep interface com.example.ui.GeminiApiService { *; }

# Keep SQLCipher database encryption classes intact for runtime JNI bindings
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

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
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# OkHttp/Okio keep rules
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Moshi rules
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# retrofit2 converters
-keep class retrofit2.converter.moshi.** { *; }
-dontwarn retrofit2.converter.moshi.**
