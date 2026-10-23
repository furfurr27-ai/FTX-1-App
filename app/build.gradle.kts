import org.gradle.api.tasks.Copy

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.n0png.fieldops.app"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "dev.n0png.fieldops"
        minSdk = 29
        targetSdk = 35
        versionCode = 69
        versionName = "0.69.0-cp0009r"
        // FieldOps is a single-device Galaxy S23 Ultra target.
        ndk { abiFilters += "arm64-v8a" }
    }

    sourceSets.getByName("main").java.srcDir(rootProject.file("core/src/main/kotlin"))
    // Consume the existing, original production FT-family source, rather
    // than duplicating/rewriting its JNI bridge in the Android application.
    sourceSets.getByName("main").java.srcDir(rootProject.file("android/compat/src/main/java"))
    sourceSets.getByName("main").java.srcDir(layout.buildDirectory.dir("generated/ft-family-kotlin"))

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

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

// Explicitly select just the FT-family adapter from the legacy multi-modem
// source tree; the JS8 provider is not yet vendor-resolved. Copying a single
// source at build time avoids accidentally packaging unlicensed/absent peers.
val stageFtFamilySource by tasks.registering(Copy::class) {
    from(rootProject.file("android/pipeline/src/main/kotlin/dev/n0png/fieldops/android/dsp/FtFamilyNativeEngine.kt"))
    into(layout.buildDirectory.dir("generated/ft-family-kotlin/dev/n0png/fieldops/android/dsp"))
}
tasks.named("preBuild") { dependsOn(stageFtFamilySource) }
