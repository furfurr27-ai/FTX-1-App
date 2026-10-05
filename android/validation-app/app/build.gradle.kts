plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.n0png.fieldops.validation"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.n0png.fieldops.validation"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "cp0003c"
        val sourceSha = System.getenv("GITHUB_SHA") ?: "local"
        buildConfigField("String", "SOURCE_SHA", "\"$sourceSha\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/kotlin",
                "../../pipeline/src/main/kotlin/dev/n0png/fieldops/android/logbook",
                "../../../core/src/main/kotlin/dev/n0png/fieldops/core/logbook",
            )
            jniLibs.srcDirs("src/main/jniLibs")
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
