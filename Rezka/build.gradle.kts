plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    language = "ru"
    authors = listOf("kiikuuchii")
    description = "Плагин поиска фильмов, сериалов и аниме с сайта rezka-ua.org"
    status = 1
    version = 1
}

android {
    namespace = "com.rezka"
    compileSdkVersion = "android-33" // Можно ставить 33 или ту, что в корневом файле

    defaultConfig {
        minSdk = 21
        targetSdk = 33
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
        val implementation by configurations
        val cloudstream by configurations
        cloudstream("com.lagradost:cloudstream3:pre-release")
        
		implementation("org.jsoup:jsoup:1.19.1")
}
