plugins {
    id("com.android.library")
    kotlin("android")
}

group = "com.yourname"
version = 1

android {
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        targetSdk = 35
    }

    // BuildConfig field для API keys
    buildTypes {
        getByName("release") {
            buildConfigField("String", "TMDB_API_KEY", "\"${project.findProperty("TMDB_API_KEY") ?: ""}\"")
            buildConfigField("String", "JIKAN_API", "\"https://api.jikan.moe/v4\"")
        }
        getByName("debug") {
            buildConfigField("String", "TMDB_API_KEY", "\"${project.findProperty("TMDB_API_KEY") ?: ""}\"")
            buildConfigField("String", "JIKAN_API", "\"https://api.jikan.moe/v4\"")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    // Cloudstream SDK is provided by the template; assume it's in parent build
}
