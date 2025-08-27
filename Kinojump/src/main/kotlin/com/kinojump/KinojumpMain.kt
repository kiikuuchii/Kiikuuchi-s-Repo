package com.kinojump

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

suspend fun MainAPI.loadKinojumpMainPage(page: Int): HomePageResponse? {
    val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"

    val categories = listOf(
        "Фильмы" to "$mainUrl/films/page/$page",
        "Сериалы" to "$mainUrl/serials/page/$page",
        "Мультфильмы" to "$mainUrl/cartoons/page/$page",
        "Мультсериалы" to "$mainUrl/cartoons/series-cartoons/page/$page",
        "Аниме" to "$mainUrl/anime/page/$page"
    )

    val homePageLists = categories.map { (title, url) ->
        val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document

        val items = doc.select(".poster.grid-item").mapNotNull { el ->
            val href = el.selectFirst(".poster__title a")?.attr("href")?.let { fixUrl(it) }
                ?: return@mapNotNull null
            val name = el.selectFirst(".poster__title")?.text() ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("data-src")?.let { fixUrl(it) }
            val typeText = el.selectFirst(".poster__subtitle li:last-child")?.text()?.lowercase()

            val type = when {
                typeText?.contains("сериал") == true -> TvType.TvSeries
                typeText?.contains("аниме") == true -> TvType.Anime
                typeText?.contains("мульт") == true && typeText.contains("сериал") -> TvType.Cartoon
                typeText?.contains("мульт") == true -> TvType.Cartoon
                else -> TvType.Movie
            }

            if (type == TvType.Movie) {
                newMovieSearchResponse(name, href, type) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(name, href, type) {
                    this.posterUrl = poster
                }
            }
        }

        HomePageList(title, items)
    }

    return newHomePageResponse(homePageLists)
}
