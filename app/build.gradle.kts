import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

val releaseSigningProperties = Properties()
val releaseSigningFile = rootProject.file("keystore.properties")
if (releaseSigningFile.isFile) {
    FileInputStream(releaseSigningFile).use(releaseSigningProperties::load)
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? {
    return providers.gradleProperty(propertyName).orNull
        ?: providers.environmentVariable(environmentName).orNull
        ?: releaseSigningProperties.getProperty(propertyName)
}

val releaseStoreFile = releaseSigningValue("CATSCAN_STORE_FILE", "CATSCAN_STORE_FILE")
val releaseStorePassword = releaseSigningValue("CATSCAN_STORE_PASSWORD", "CATSCAN_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("CATSCAN_KEY_ALIAS", "CATSCAN_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("CATSCAN_KEY_PASSWORD", "CATSCAN_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
if (releaseTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is not configured. Copy keystore.properties.example " +
            "to keystore.properties and provide the real values, or set the CATSCAN_* environment variables."
    )
}

android {
    namespace = "com.example.catscandemo"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.catscandemo"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "3.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        // CPU 架构拆分：为不同架构生成独立 APK
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true  // 同时生成一个包含所有架构的通用 APK
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true  // 移除未使用资源
            isDebuggable = false       // 禁用调试
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM锛堝彧淇濈暀杩欎竴澶勶級
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Compose 鏍稿績锛歎I / Foundation / Animation / Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.compose.foundation:foundation")

    // Image processing engine
    implementation("org.opencv:opencv:4.9.0")
    implementation("androidx.compose.material3:material3")

    // FlashlightOn/Off / PhotoLibrary 绛夊浘鏍?
    implementation("androidx.compose.material:material-icons-extended")

    // 娴嬭瘯/璋冭瘯
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // 浣犵殑鍏跺畠渚濊禆鐓ф棫淇濈暀锛堢ず渚嬶級
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("androidx.compose.animation:animation")

    // Hilt 渚濊禆
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
