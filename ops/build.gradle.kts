plugins {
    id("com.android.application")
}

android {
    namespace = "com.garsyanimultiusaha.gmuedutrans.ops"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.garsyanimultiusaha.gmuedutrans.ops"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
