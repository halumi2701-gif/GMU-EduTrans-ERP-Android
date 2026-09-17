plugins {
    id("com.android.application")
}

android {
    namespace = "id.zenstars.rukunya"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.zenstars.rukunya"
        minSdk = 23
        targetSdk = 35
        versionCode = 15
        versionName = "1.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
