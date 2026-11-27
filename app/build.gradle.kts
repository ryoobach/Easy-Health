import java.time.LocalDate

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val testApp = providers.gradleProperty("testApp").orNull == "true"
android {
    namespace = "com.ryoo.easyhealth"
    compileSdk = 35
    defaultConfig {
        applicationId = if (testApp) "com.ryoo.easyhealth.test143" else "com.ryoo.easyhealth"
        minSdk = 26
        targetSdk = 35
        versionCode = 49
        versionName = "1.49"
        manifestPlaceholders["appLabel"] = if (testApp) "Easy Health 1.49 TEST" else "Easy Health"
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
    }
    // Always sign every debug APK with its designated key. The release workflow
    // requires the established production key; the isolated test workflow uses
    // a separate test-only key and package ID.
    signingConfigs {
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
