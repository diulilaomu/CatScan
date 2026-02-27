# Add project specific ProGuard rules here.

# ========== 通用优化规则 ==========
# 移除日志
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ========== Hilt ==========
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# ========== Compose ==========
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ========== CameraX ==========
-keep class androidx.camera.** { *; }

# ========== ML Kit ==========
-keep class com.google.mlkit.** { *; }

# ========== OkHttp ==========
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ========== OpenCV ==========
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# ========== Kotlin ==========
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ========== 保留数据模型 ==========
-keep class com.example.catscandemo.domain.model.** { *; }
-keep class com.example.catscandemo.data.network.** { *; }

# ========== 移除未使用的 Native 库 ==========
# 只保留必要的 ABI