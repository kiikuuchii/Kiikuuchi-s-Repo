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
    val url = "$mainUrl/search/?do=search&subaction=search&q=$query"
    val response = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0"))
    val document = response.document

    val items = document.select("div.b-content__inline_item")
    if (items.isEmpty()) {
        println("Rezka search: ничего не найдено. Возможно изменилась разметка.")
    }

    return items.mapNotNull { element ->
        val title = element.selectFirst(".b-content__inline_item-link a")?.text() ?: return@mapNotNull null
        val href = element.attr("data-url") ?: return@mapNotNull null
        val poster = element.selectFirst("img")?.attr("src")

        val type = when {
            href.contains("/animation/") -> TvType.Anime
            href.contains("/series/") -> TvType.TvSeries
            else -> TvType.Movie
        }

        newMovieSearchResponse(title, href, type) {
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