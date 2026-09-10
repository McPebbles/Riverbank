// NOTE: these imports are hoisted deliberately. Inside a Kotlin DSL build
// script for a module that applies a Java-based plugin (AGP applies
// JavaBasePlugin), the bare identifier `java` resolves to the generated
// JavaPluginExtension accessor, not to the java package. Writing
// `java.util.Properties()` inline therefore fails with "unresolved reference:
// util". Import the type at the top and use the short name.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.riverbank.shop"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.riverbank.shop"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // No test runner, no analytics, no crash reporter, no ad SDK.
    }

    signingConfigs {
        // Release signing is supplied out-of-band so no secret ever lands in git.
        // Provide these via keystore.properties (git-ignored) or CI environment vars.
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            val envKeystore: String? = System.getenv("RIVERBANK_KEYSTORE")
            if (propsFile.exists()) {
                val props = Properties()
                FileInputStream(propsFile).use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else if (!envKeystore.isNullOrBlank()) {
                storeFile = rootProject.file(envKeystore)
                storePassword = System.getenv("RIVERBANK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RIVERBANK_KEY_ALIAS")
                keyPassword = System.getenv("RIVERBANK_KEY_PASSWORD")
            }
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            // Reproducible-build friendliness: no build-time timestamps in the output.
            ndk { debugSymbolLevel = "NONE" }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    dependenciesInfo {
        // Do not embed the Google Play dependency blob (it is an opaque, encrypted payload).
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.google.material)
}
