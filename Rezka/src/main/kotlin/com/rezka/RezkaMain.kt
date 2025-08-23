package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


    suspend fun MainAPI.loadRezkaMainPage(page: Int): HomePageResponse {
    val categories = listOf(
        "🎬 Фильмы" to "$mainUrl/page/$page/?filter=watching&genre=1",
        "📺 Сериалы" to "$mainUrl/page/$page/?filter=watching&genre=2",
        "🎞️ Мультфильмы" to "$mainUrl/page/$page/?filter=watching&genre=3",
        "🍥 Аниме" to "$mainUrl/page/$page/?filter=watching&genre=82",
    )

    return newHomePageResponse(
        categories.map { (title, url) ->
            val doc = app.get(url).document
            val items = doc.select(".b-content__inline_item").mapNotNull { element ->
                val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val name = element.selectFirst(".b-content__inline_item-link")?.text() ?: return@mapNotNull null
                val poster = element.selectFirst("img")?.attr("src")
                val year = element.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()

                val type = when (title) {
                    "🎬 Фильмы" -> TvType.Movie
                    "📺 Сериалы" -> TvType.TvSeries
                    "🎞️ Мультфильмы" -> TvType.Cartoon
                    "🍥 Аниме" -> TvType.Anime
                    else -> TvType.Movie
                }

                newMovieSearchResponse(name, href, type) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
            HomePageList(title, items)
        }
    )
}
