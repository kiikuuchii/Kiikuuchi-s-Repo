package com.kinojump

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.HomePageResponse

suspend fun MainAPI.loadKinojumpMainPage(page: Int): HomePageResponse {
    val url = "${mainUrl}/page/${page}"
    val doc = app.get(url).document

    val items = doc.select(".short-list .short-item").mapNotNull { element ->
        val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
        val name = element.selectFirst(".short-title")?.text() ?: return@mapNotNull null
        val poster = element.selectFirst(".short-img-holder img")?.attr("data-src")
        val year = element.selectFirst(".short-date")?.text()?.toIntOrNull()

        // Определение типа по URL-адресу
        val type = when {
            href.contains("/anime/") -> TvType.Anime
            href.contains("/cartoon/") -> TvType.Cartoon
            href.contains("/series/") -> TvType.TvSeries
            else -> TvType.Movie
        }

        // Возвращаем правильный тип SearchResponse
        newMovieSearchResponse(name, href, type) {
            this.posterUrl = poster
            this.year = year
        }
    }

    return newHomePageResponse(
        HomePageList("Главная страница", items)
    )
}
