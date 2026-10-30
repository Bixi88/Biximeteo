plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.meteopersonale"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.meteopersonale"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // MET Norway richiede uno User-Agent che identifichi l'app e un contatto
        buildConfigField("String", "USER_AGENT", "\"MeteoPersonale/0.1 github.com/Bixi88\"")
    }

    // Firma fissa: serve perché ogni build su GitHub produca un APK
    // aggiornabile sopra quello già installato (senza disinstallare).
    signingConfigs {
        create("personal") {
            storeFile = file("personal.keystore")
            storePassword = "meteopersonale"
            keyAlias = "meteo"
            keyPassword = "meteopersonale"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
        debug {
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    lint {
        // la build non deve fallire per avvisi di lint
        abortOnError = false
        checkReleaseBuilds = false
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
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
