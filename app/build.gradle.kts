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
        versionCode = 64
        versionName = "0.64.0-cp0009m"
    }

    sourceSets.getByName("main").java.srcDir(rootProject.file("core/src/main/kotlin"))

    testOptions {
        unitTests.isIncludeAndroidResources = true
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

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
