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
        versionCode = 2
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // Unminified: ShizukuShell reaches Shizuku's newProcess by reflection.
            isMinifyEnabled = false
            // Set by the release workflow; a local build without them stays unsigned.
            System.getenv("KEYSTORE_FILE")?.let { keystore ->
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystore)
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}
