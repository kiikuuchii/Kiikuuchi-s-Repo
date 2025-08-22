package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import org.jsoup.Jsoup

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka-ua.org"
    override var name = "Rezka"
    override val hasMainPage = true
    override var lang = "ru"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override suspend fun search(query: String): List<SearchResponse> {
    val url = "$mainUrl/index.php?do=search&subaction=search&q=$query"
    val response = app.get(url)

    // ЛОГИРУЕМ HTML в консоль
    println(">>> SEARCH URL: $url")
    println(">>> RESPONSE: ${response.text.take(500)}") // первые 500 символов

    val document = response.document
    val results = document.select("div.b-content__inline_item")

    println(">>> FOUND ELEMENTS: ${results.size}")

    return results.mapNotNull { element ->
        val title = element.selectFirst("div.b-content__inline_item-link a")?.text()
        val href = element.selectFirst("div.b-content__inline_item-link a")?.attr("href")
        if (title == null || href == null) return@mapNotNull null

        val poster = element.selectFirst("div.b-content__inline_item-cover img")?.attr("src")

        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }
}


    // 📄 Загрузка страницы фильма/сериала
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.b-post__title h1")?.text() ?: return null
        val poster = document.selectFirst("div.b-sidecover img")?.attr("src")
        val description = document.selectFirst("div.b-post__description_text")?.text()
        val year = document.select("table.b-post__info tr:contains(Год:) td").text().toIntOrNull()

        // Определение типа
        val isAnime = document.select("table.b-post__info tr:contains(Жанр:)").text().contains("Аниме", ignoreCase = true)
        val type = when {
            url.contains("/films/") -> TvType.Movie
            isAnime -> TvType.Anime
            else -> TvType.TvSeries
        }

            return when (type) {
        TvType.Movie -> newMovieLoadResponse(title, url, type, url) { // тут dataUrl нужен
            this.posterUrl = poster
            this.year = year
            this.plot = description
        }

        TvType.Anime, TvType.TvSeries -> newTvSeriesLoadResponse(title, url, type, emptyList()) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
        }

          else -> null
       }
    }
}