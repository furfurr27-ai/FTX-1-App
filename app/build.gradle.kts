plugins {
    id("com.android.application")
}

android {
    namespace = "dev.n0png.fieldops.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.n0png.fieldops"
        minSdk = 29
        targetSdk = 35
        versionCode = 55
        versionName = "0.55.0-cp0009d"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
