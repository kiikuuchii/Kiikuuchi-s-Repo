import org.jetbrains.kotlin.konan.properties.Properties

version = 451

android {
   buildFeatures {
       buildConfig = true
	}
	defaultConfig {
	    val properties = Properties()
		properties.load(project.rootProject.file("local.properties").inputStream())
   }
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
