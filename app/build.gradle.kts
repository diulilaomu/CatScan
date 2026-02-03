plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
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
        versionCode = 1
        versionName = "3.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true  // 移除未使用资源
            isDebuggable = false       // 禁用调试
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

    // Compose BOM（只保留这一处）
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Compose 核心：UI / Foundation / Animation / Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")

    // animateDpAsState / tween 需要 animation-core
    implementation("androidx.compose.animation:animation-core")

    implementation("androidx.compose.material3:material3")

    // FlashlightOn/Off / PhotoLibrary 等图标
    implementation("androidx.compose.material:material-icons-extended")

    // 测试/调试
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // 你的其它依赖照旧保留（示例）
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")  // 用于定位条码位置
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation(libs.ads.mobile.sdk)
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-text")

    // Hilt 依赖
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
