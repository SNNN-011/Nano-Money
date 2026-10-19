# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve annotations, signatures, and inner-class metadata needed at runtime.
# L2: SourceFile & LineNumberTable SENGAJA tidak dipertahankan — nama file dan nomor baris
# tidak boleh bocor di stack trace rilis (deobfuscation tetap pakai mapping.txt).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# L2: aturan keep konservatif untuk kelas yang diinstansiasi via refleksi oleh framework.
# WorkManager membangun ulang Worker berdasarkan nama kelas (bukan referensi langsung runtime),
# jadi paket worker dijaga utuh.
-keep class com.example.worker.** { *; }

# Keep only standard non-sensitive BuildConfig fields needed at runtime
-keep class com.example.BuildConfig {
    public static final boolean DEBUG;
    public static final java.lang.String APPLICATION_ID;
    public static final int VERSION_CODE;
    public static final java.lang.String VERSION_NAME;
}

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

# JNI: nama kelas & method native ikut jadi bagian simbol C (needle_jni.c,
# Java_com_example_data_local_NeedleNative_*). Dua baris di atas sudah menjaga
# paket com.example.data.** apa adanya, jadi kelas NeedleNative aman; aturan
# umum di bawah menutup JNI lain yang mungkin ditambahkan di luar paket itu.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

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
