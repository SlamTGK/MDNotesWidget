plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mdnotes.widget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mdnotes.widget"
        minSdk = 36
        targetSdk = 36
        versionCode = 3
        versionName = "1.3"
    }

    signingConfigs {
        // Reuse the built-in debug keystore for sideloading.
        // For Play Store, generate a real keystore and use GitHub Secrets.
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false   // disabled — R8 can break widgets without careful keep rules
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
}
