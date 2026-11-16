import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.resttimer.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.resttimer.wear"
        minSdk = 30          // Wear OS 3 이상 (갤럭시워치6: Wear OS 4/5)
        targetSdk = 35       // Google Play: Wear OS 앱은 API 35 이상 필수
        versionCode = 4
        versionName = "1.3"
    }

    // Play 업로드 키: rest-timer-watch/keystore.properties (git 에 올리지 않음)
    val keystorePropsFile = rootProject.file("keystore.properties")
    val uploadSigning = if (keystorePropsFile.exists()) {
        val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
        signingConfigs.create("upload") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        release {
            isMinifyEnabled = false
            // 업로드 키가 없으면 개인 설치용으로 debug 키 서명
            signingConfig = uploadSigning ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-ongoing:1.0.0")

    testImplementation("junit:junit:4.13.2")
}
