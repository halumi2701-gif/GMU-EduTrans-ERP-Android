plugins {
    id("com.android.application")
}

android {
    namespace = "com.zenstars.rukunya"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zenstars.rukunya"
        minSdk = 24
        targetSdk = 35
        versionCode = 15
        versionName = "1.5"
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
