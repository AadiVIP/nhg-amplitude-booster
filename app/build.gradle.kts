import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing material lives outside this repository. Provide a properties
// file (storeFile, storePassword, keyAlias, keyPassword) either through the
// NHG_RELEASE_SIGNING_PROPERTIES environment variable, the
// nhg.release.signingProperties Gradle property, or a git-ignored
// keystore.properties next to this script. With none of them, release builds
// come out unsigned rather than silently falling back to the Android debug key.
val releaseSigningFile = sequenceOf(
    System.getenv("NHG_RELEASE_SIGNING_PROPERTIES"),
    providers.gradleProperty("nhg.release.signingProperties").orNull,
    file("keystore.properties").path
).filterNotNull().map(::File).firstOrNull { it.isFile }

val releaseSigning = Properties().apply {
    releaseSigningFile?.let { FileInputStream(it).use(::load) }
}

val releaseSigningReady = releaseSigningFile != null &&
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !releaseSigning.getProperty(it).isNullOrBlank() }

logger.lifecycle(
    if (releaseSigningReady) {
        "NHG Amplitude Booster release signing: active (parameters from ${releaseSigningFile?.absolutePath})"
    } else {
        "NHG Amplitude Booster release signing: NOT configured - assembleRelease produces an unsigned APK"
    }
)

android {
    namespace = "com.noheadphonegamerz.amplitudebooster"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.noheadphonegamerz.amplitudebooster"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-graphics:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")

    testImplementation("junit:junit:4.13.2")
}
