package com.kinojump

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.HomePageResponse

suspend fun MainAPI.loadKinojumpMainPage(page: Int): HomePageResponse {
    val categories = listOf(
        "Фильмы" to "$mainUrl/films/page/$page",
        "Сериалы" to "$mainUrl/serials/page/$page",
        "Мультфильмы" to "$mainUrl/cartoons/page/$page",
        "Мультсериалы" to "$mainUrl/cartoons/series-cartoons/page/$page",
        "Аниме" to "$mainUrl/anime/page/$page"
    )

    val homePageLists = categories.map { (title, url) ->
        val doc = app.get(url).document

        val items = mutableListOf<SearchResponse>()

        // 🔹 Блок "сейчас смотрят" (слайдер)
        doc.select(".owl-item a.top").mapNotNull { el ->
            val href = el.attr("href") ?: return@mapNotNull null
            val name = el.selectFirst(".top__title")?.text() ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src")

            newMovieSearchResponse(name, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }.let { items.addAll(it) }

        // 🔹 Блок "новинки" (основная сетка)
        doc.select(".poster.grid-item").mapNotNull { el ->
            val href = el.selectFirst(".poster__title a")?.attr("href") ?: return@mapNotNull null
            val name = el.selectFirst(".poster__title")?.text() ?: return@mapNotNull null
            val poster = el.selectFirst("img[data-src]")?.attr("data-src")
                ?: el.selectFirst("img")?.attr("src")
            val year = el.selectFirst(".poster__subtitle li")?.text()?.toIntOrNull()
            val typeText = el.selectFirst(".poster__subtitle li:last-child")?.text()?.lowercase()

            val type = when {
                typeText?.contains("сериал") == true -> TvType.TvSeries
                typeText?.contains("аниме") == true -> TvType.Anime
                typeText?.contains("мульт") == true && typeText.contains("сериал") -> TvType.Cartoon
                typeText?.contains("мульт") == true -> TvType.Cartoon
                else -> TvType.Movie
            }

            when (type) {
                TvType.Movie -> newMovieSearchResponse(name, href, type) {
                    this.posterUrl = poster
                    this.year = year
                }
                else -> newTvSeriesSearchResponse(name, href, type) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
        }.let { items.addAll(it) }

        HomePageList(title, items)
    }

    return newHomePageResponse(homePageLists)
}

