plugins {
    id("com.android.application")
}

fun esc(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val supabaseUrl = providers.gradleProperty("GAWONE_SUPABASE_URL")
    .orElse(providers.environmentVariable("GAWONE_SUPABASE_URL"))
    .orElse("https://fhtxlojbguineyqayhai.supabase.co")
    .get()
val supabaseKey = providers.gradleProperty("GAWONE_SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.environmentVariable("GAWONE_SUPABASE_PUBLISHABLE_KEY"))
    .orElse("sb_publishable_kMYXQocud4kbds7q5fWG-A_Lr-5Xl3h")
    .get()

android {
    namespace = "site.garsyanimultiusaha.gawone.management"
    compileSdk = 35

    defaultConfig {
        applicationId = "site.garsyanimultiusaha.gawone.management"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-m2.22-rc3"
        buildConfigField("String", "SUPABASE_URL", esc(supabaseUrl))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", esc(supabaseKey))
        buildConfigField("String", "BACKEND_CONTRACT", "\"M2.22\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release { isMinifyEnabled = false; isShrinkResources = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
