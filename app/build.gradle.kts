plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.akrishna.docconverter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.akrishna.docconverter"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.0"
    }

    // Release signing comes from the environment (e.g. GitHub secrets); no key is stored in the repo.
    // Without it the release build falls back to the debug signature, which installs fine but
    // cannot update an APK signed by a different machine's debug key.
    val releaseKey = System.getenv("KEYSTORE_FILE")
    if (releaseKey != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKey)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (releaseKey != null) "release" else "debug")
            // R8 is off: shrinking PdfBox without device testing risks runtime crashes.
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
