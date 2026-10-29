plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.clintonium.es_de_overlay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clintonium.es_de_overlay"
        minSdk = 33
        // Pinned: Android 14 (34) requires a new MediaProjection consent per capture.
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    testImplementation("junit:junit:4.13.2")
}
