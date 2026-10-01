plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.halilkhrmn.dpimech"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.halilkhrmn.dpimech"
        minSdk = 26
        targetSdk = 36
        // Release tags are vX.Y.Z and versionName must match (checked by the release workflow).
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from the environment (CI secrets, see docs/RELEASING.md); without it
    // release APKs are built unsigned.
    val keystore = System.getenv("DPIMECH_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("DPIMECH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DPIMECH_KEY_ALIAS")
                keyPassword = System.getenv("DPIMECH_KEY_PASSWORD")
            }
        }
    }

    // One APK per ABI plus a universal one (the website and IzzyOnDroid offer the universal APK).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // ciadpi is executed from nativeLibraryDir, so native libraries must be extracted.
        jniLibs.useLegacyPackaging = true
    }
    dependenciesInfo {
        // F-Droid / IzzyOnDroid: no Google-encrypted dependency blob in the APK.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
}
