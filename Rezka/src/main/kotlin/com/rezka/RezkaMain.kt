package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


    suspend fun MainAPI.loadRezkaMainPage(page: Int): HomePageResponse {
    val url = "https://rezka-ua.org/?filter=watching"
    val doc = app.get(url).document

    val films = mutableListOf<SearchResponse>()
    val series = mutableListOf<SearchResponse>()
    val cartoons = mutableListOf<SearchResponse>()
    val anime = mutableListOf<SearchResponse>()

    doc.select(".b-content__inline_item").forEach { element ->
        val href = element.selectFirst("a")?.attr("href") ?: return@forEach
        val title = element.selectFirst(".b-content__inline_item-link")?.text() ?: return@forEach
        val poster = element.selectFirst("img")?.attr("src")
        val year = element.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()

        val type = when {
            href.contains("/films/") -> TvType.Movie
            href.contains("/series/") -> TvType.TvSeries
            href.contains("/cartoons/") -> TvType.Cartoon
            href.contains("/anime/") -> TvType.Anime
            else -> TvType.Movie
        }

        val item = newMovieSearchResponse(title, href, type) {
            this.posterUrl = poster
            this.year = year
        }

        when (type) {
            TvType.Movie -> films.add(item)
            TvType.TvSeries -> series.add(item)
            TvType.Cartoon -> cartoons.add(item)
            TvType.Anime -> anime.add(item)
            else -> {}
        }
    }

    return newHomePageResponse(
        listOf(
            HomePageList("🎬 Фильмы", films),
            HomePageList("📺 Сериалы", series),
            HomePageList("🎞️ Мультфильмы", cartoons),
            HomePageList("🍥 Аниме", anime)
        )
    )
}
