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
        versionName = System.getenv("TB_VERSION_NAME") ?: "1.0.0"
        // 개발자 도구(캡처용 시험 데이터 · 장면 열기): debug 빌드에만
        buildConfigField("boolean", "DEV_TOOLS", "true")
        // 낭독 음원 공개 주소 (Cloudflare R2). 비밀이 아니라 GitHub Variables 에서 빌드할 때 넣음. 비면 GitHub 릴리스만.
        buildConfigField("String", "NARRATION_URL", "\"${(System.getenv("NARRATION_URL") ?: "").trimEnd('/')}\"")
    }

    // 직접 설치용 (내 폰 시험용) 고정 키: 저장소에 있는 시험 키라 비밀이 아님 (android/keystore/README.md).
    // Play 업로드 키는 따로, GitHub Secrets 에만.
    signingConfigs {
        create("sideload") {
            storeFile = file("../keystore/sideload.jks")
            storePassword = "android"; keyAlias = "sideload"; keyPassword = "android"
        }
        // Play 올리기용 키: 저장소에 없고, 출시 빌드 (android-release.yml) 가 Secrets 에서 꺼내 씀
        System.getenv("PLAY_KEYSTORE")?.let { path ->
            create("play") {
                storeFile = file(path)
                storePassword = System.getenv("PLAY_KEYSTORE_PASSWORD"); keyAlias = System.getenv("PLAY_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("PLAY_KEY_PASSWORD") ?: System.getenv("PLAY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sideload"); versionNameSuffix = "-dev" }
        release {
            // R8: 줄이고 섞기. 오류 위치는 mapping.txt 로 되찾아요 (출시 워크플로가 함께 올림)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("play") ?: signingConfigs.getByName("sideload")
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
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.google.android.play:review-ktx:2.0.2")
}
