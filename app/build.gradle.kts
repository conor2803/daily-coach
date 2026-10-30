plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.conor2803.coach"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.conor2803.coach"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    // Background check-ins
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Garmin + RingConn data via Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Places (geofences) and current location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // HTTP for the Claude API and the weather
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // On-device engine: runs Gemma on the phone
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in unit tests; this is the real implementation
    testImplementation("org.json:json:20260814")
}
