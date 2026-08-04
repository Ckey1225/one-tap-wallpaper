plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.wallpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wallpaper"
        minSdk = 24            // Android 7.0，覆盖锁屏壁纸 API（API 24+）
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // 关闭混淆（保持简单），如需开启请补充 proguard 规则
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 使用 debug 签名（无自定义密钥库，便于直接安装）
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    // Compose 编译器版本需与 Kotlin 版本匹配（Kotlin 1.9.22 ↔ Compose Compiler 1.5.8）
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // AndroidX 基础
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose（通过 BOM 统一版本）
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // ViewModel + 协程
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 网络请求（OkHttp，轻量、支持异步与重试）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // SAF 自定义缓存目录（DocumentFile 统一 File / content:// 两种存储）
    implementation("androidx.documentfile:documentfile:1.0.1")
}