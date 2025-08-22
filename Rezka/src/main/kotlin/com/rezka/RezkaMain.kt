package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class RezkaMain(private val api: MainAPI) {
    private val mainUrl = "https://rezka-ua.org"

    suspend fun getMainPage(page: Int): HomePageResponse {
        val doc = app.get(mainUrl).document

        val homeSections = ArrayList<HomePageList>()

        // Фильмы
        val movies = doc.select(".b-content__inline_items .b-content__inline_item").mapNotNull {
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.selectFirst(".b-content__inline_item-link")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            val year = it.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()
            api.newMovieSearchResponse(title, href, TvType.Movie) {   // <-- используем api
                this.posterUrl = poster
                this.year = year
            }
        }
        homeSections.add(HomePageList("Фильмы", movies))

        // Сериалы
        val series = doc.select(".b-content__inline_items .b-content__inline_item").mapNotNull {
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            if (!href.contains("/series/")) return@mapNotNull null
            val title = it.selectFirst(".b-content__inline_item-link")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            val year = it.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()
            api.newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
            }
        }
        if (series.isNotEmpty()) {
            homeSections.add(HomePageList("Сериалы", series))
        }

        // Аниме
        val anime = doc.select(".b-content__inline_items .b-content__inline_item").mapNotNull {
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            if (!href.contains("/anime/")) return@mapNotNull null
            val title = it.selectFirst(".b-content__inline_item-link")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            val year = it.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()
            api.newMovieSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
                this.year = year
            }
        }
        if (anime.isNotEmpty()) {
            homeSections.add(HomePageList("Аниме", anime))
        }

        return newHomePageResponse(homeSections)
    }
}
