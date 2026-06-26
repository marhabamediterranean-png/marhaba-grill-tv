plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mpls.salattv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mpls.salattv"
        minSdk = 24
        targetSdk = 34
        // versionCode is supplied by CI (-PversionCode=<n>) so each GitHub build
        // gets a higher number, which drives over-the-air updates. Falls back to
        // 1000 for local builds.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1000
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"
    }

    buildTypes {
        // Debug build is auto-signed with the Android debug key, so the APK
        // installs on any device/TV with zero signing setup. Kept un-minified
        // to maximize stability (no obfuscation surprises).
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Media3 / ExoPlayer for the 24/7 Makkah HLS stream + adhan audio
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Coroutines for background prayer-time fetching
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
