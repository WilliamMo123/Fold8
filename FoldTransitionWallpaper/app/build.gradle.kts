plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wmo.foldwallpaper"
    // RuntimeShader (AGSL) requires API 33+, hinge angle sensor is API 33+ (Sensor.TYPE_HINGE_ANGLE)
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wmo.foldwallpaper"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Jetpack WindowManager - fallback / additional posture info (FoldingFeature),
    // used only as a secondary source; primary signal is Sensor.TYPE_HINGE_ANGLE.
    implementation("androidx.window:window:1.3.0")
}
