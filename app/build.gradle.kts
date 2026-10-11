plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val signingKeystorePath = System.getenv("RIFT_SIGNING_KEYSTORE_PATH")
val signingKeystorePassword = System.getenv("RIFT_SIGNING_KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("RIFT_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("RIFT_SIGNING_KEY_PASSWORD")
val permanentSigningConfigured = listOf(
    signingKeystorePath,
    signingKeystorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.dizzyvy.rift"
    compileSdk = 37

    signingConfigs {
        if (permanentSigningConfigured) {
            create("permanent") {
                storeFile = file(requireNotNull(signingKeystorePath))
                storePassword = requireNotNull(signingKeystorePassword)
                keyAlias = requireNotNull(signingKeyAlias)
                keyPassword = requireNotNull(signingKeyPassword)
            }
        }
    }

    defaultConfig {
        // Keep stable so existing installations retain their app-owned data during the rebrand.
        applicationId = "com.dizzyvy.sjmusicapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "0.12.0"
    }

    buildTypes {
        getByName("debug") {
            if (permanentSigningConfigured) {
                signingConfig = signingConfigs.getByName("permanent")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
