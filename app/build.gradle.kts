plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tatsu.homehub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tatsu.homehub"
        minSdk = 28
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 7
        versionName = System.getenv("VERSION_NAME") ?: "0.4.1"
    }

    val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    if (!releaseStorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui:1.8.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.0")
    implementation("androidx.compose.foundation:foundation:1.8.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.0")
}
