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
        versionCode = 3
        versionName = "1.1.0"
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

    // The JUnit 5 platform for Jazzer's fuzz tests; the JUnit 4 tests run on its vintage engine.
    testOptions.unitTests.all {
        it.useJUnitPlatform()
        // Fuzzing (JAZZER_FUZZ set): a fuzz test's @Timeout limits each input, which Jazzer hands to libFuzzer,
        // so JUnit mustn't apply it to the whole run as well.
        if (System.getenv("JAZZER_FUZZ") != null) it.systemProperty("junit.jupiter.execution.timeout.mode", "disabled")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
    // JUnit 5, which Jazzer 0.30.0 is built against; the JUnit 4 tests run on its vintage engine.
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
