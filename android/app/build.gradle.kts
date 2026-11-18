import java.util.Properties
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

fun resolveReleaseConfig(envName: String, propertyKey: String): String? =
    providers.environmentVariable(envName).orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: keystoreProperties.getProperty(propertyKey)?.trim()?.takeIf { it.isNotEmpty() }

val releaseStoreFilePath = resolveReleaseConfig("FLARETUNE_RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = resolveReleaseConfig("FLARETUNE_RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = resolveReleaseConfig("FLARETUNE_RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = resolveReleaseConfig("FLARETUNE_RELEASE_KEY_PASSWORD", "keyPassword")
val anyReleaseSigningConfigured = keystorePropertiesFile.isFile ||
    listOf(
        releaseStoreFilePath,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).any { it != null }
val resolvedReleaseStoreFile = releaseStoreFilePath?.let { rootProject.file(it) }
val hasCompleteReleaseSigning = if (!anyReleaseSigningConfigured) {
    false
} else {
    if (resolvedReleaseStoreFile == null || !resolvedReleaseStoreFile.isFile) {
        throw GradleException(
            "Release signing is partially configured, but keystore file was not found: " +
                "${resolvedReleaseStoreFile?.absolutePath ?: "<missing storeFile>"}",
        )
    }
    if (releaseStorePassword == null || releaseKeyAlias == null || releaseKeyPassword == null) {
        throw GradleException(
            "Release signing requires storeFile, storePassword, keyAlias, and keyPassword " +
                "in android/keystore.properties or FLARETUNE_RELEASE_* environment variables.",
        )
    }
    true
}

android {
    namespace = "com.arcinks.flaretune"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.arcinks.flaretune"
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 10
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = resolvedReleaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("flaretune.screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.browser)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.okhttp.tls)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.json.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
