plugins {
    id("com.android.application")
}

android {
    namespace = "com.vexel.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vexel.launcher"
        minSdk = 31
        targetSdk = 35
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
}

dependencies {
    implementation("androidx.viewpager2:viewpager2:1.1.0")
}

