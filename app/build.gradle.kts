plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val firebaseAppId = providers.gradleProperty("GMU_FIREBASE_APP_ID")
    .orElse(providers.environmentVariable("GMU_FIREBASE_APP_ID"))
    .orElse("")
    .get()
val firebaseApiKey = providers.gradleProperty("GMU_FIREBASE_API_KEY")
    .orElse(providers.environmentVariable("GMU_FIREBASE_API_KEY"))
    .orElse("")
    .get()
val firebaseProjectId = providers.gradleProperty("GMU_FIREBASE_PROJECT_ID")
    .orElse(providers.environmentVariable("GMU_FIREBASE_PROJECT_ID"))
    .orElse("")
    .get()
val firebaseSenderId = providers.gradleProperty("GMU_FIREBASE_SENDER_ID")
    .orElse(providers.environmentVariable("GMU_FIREBASE_SENDER_ID"))
    .orElse("")
    .get()

android {
    namespace = "com.garsyanimultiusaha.gmuedutrans.erp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.garsyanimultiusaha.gmuedutrans.erp"
        minSdk = 23
        targetSdk = 35
        versionCode = 508
        versionName = "5.0.0-alpha9"

        buildConfigField("String", "SUPABASE_URL", "\"https://gtgnwasijweewmaubvyg.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_cbTtSEhcXsHKDdldocSw3Q_bTcfXtaW\"")
        buildConfigField("String", "FIREBASE_APP_ID", buildConfigString(firebaseAppId))
        buildConfigField("String", "FIREBASE_API_KEY", buildConfigString(firebaseApiKey))
        buildConfigField("String", "FIREBASE_PROJECT_ID", buildConfigString(firebaseProjectId))
        buildConfigField("String", "FIREBASE_SENDER_ID", buildConfigString(firebaseSenderId))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("io.coil-kt:coil-compose:2.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
