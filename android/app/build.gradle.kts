plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.graviton94.todaybible"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.graviton94.todaybible"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("TB_VERSION_NAME") ?: "0.1.0"
        // 개발자 도구(캡처용 시험 데이터 · 장면 열기): debug 빌드에만
        buildConfigField("boolean", "DEV_TOOLS", "true")
    }

    // 직접 설치용 (내 폰 시험용) 고정 키: 저장소에 있는 시험 키라 비밀이 아님 (android/keystore/README.md).
    // Play 업로드 키는 따로, GitHub Secrets 에만.
    signingConfigs {
        create("sideload") {
            storeFile = file("../keystore/sideload.jks")
            storePassword = "android"; keyAlias = "sideload"; keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sideload") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
            buildConfigField("boolean", "DEV_TOOLS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.android.billingclient:billing-ktx:8.0.0")
}
