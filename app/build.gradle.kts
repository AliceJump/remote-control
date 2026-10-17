plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.alicejump.remotecontrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.alicejump.remotecontrol"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
