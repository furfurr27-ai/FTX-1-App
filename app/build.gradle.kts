plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.n0png.fieldops.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.n0png.fieldops"
        minSdk = 29
        targetSdk = 35
        versionCode = 56
        versionName = "0.56.0-cp0009e"
    }

    sourceSets.getByName("main").java.srcDir(rootProject.file("core/src/main/kotlin"))

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
