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
	tvTypes = listOf(
	      "Movie",
		  "TvSeries",
		  "Anime",
		  )
		  isCrossPlatform = true
}

android {
    buildFeatures {
        buildConfig = true
	}
}	

