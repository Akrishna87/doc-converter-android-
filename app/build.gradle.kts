plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.akrishna.docconverter"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.akrishna.docmorph"
        minSdk = 29
        targetSdk = 34
        versionCode = providers.gradleProperty("docmorphVersionCode").orElse("5").get().toInt().also {
            require(it in 5..2100000000) { "Invalid DocMorph version code" }
        }
        versionName = "1.1.1"
    }

    // No debug-key fallback. Without credentials CI builds an unsigned release.
    // Distribution workflows must require credentials and verify the signed APK.
    val signingNames = listOf("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    val signingValues = signingNames.associateWith { System.getenv(it) }
    val signingPresent = signingValues.values.any { !it.isNullOrBlank() }
    if (signingPresent) {
        require(signingValues.values.all { !it.isNullOrBlank() }) { "Incomplete release signing environment" }
        require(file(signingValues.getValue("KEYSTORE_FILE")!!).isFile) { "Release keystore missing" }
        signingConfigs {
            create("release") {
                storeFile = file(signingValues.getValue("KEYSTORE_FILE")!!)
                storePassword = signingValues.getValue("KEYSTORE_PASSWORD")
                keyAlias = signingValues.getValue("KEY_ALIAS")
                keyPassword = signingValues.getValue("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (signingPresent) signingConfig = signingConfigs.getByName("release")
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
