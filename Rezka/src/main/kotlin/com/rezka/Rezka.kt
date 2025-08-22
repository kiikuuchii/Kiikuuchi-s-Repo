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

    override suspend fun load(url: String): LoadResponse? {
    val doc = app.get(url).document

    val title = doc.selectFirst("div.b-post__title h1")?.text() ?: return null
    val poster = doc.selectFirst("div.b-sidecover img")?.attr("src")
    val fullPlot = doc.selectFirst("div.b-post__description_text")?.text()

    // таблица с инфой
    val infoTable = doc.select("table.b-post__info tr")
    var year: Int? = null
    var genres: List<String>? = null
    var country: String? = null
    var director: String? = null
    var actors: String? = null

    for (row in infoTable) {
        val key = row.selectFirst("td.l")?.text()?.trim() ?: continue
        val value = row.selectFirst("td.r")?.text()?.trim() ?: continue

        when {
            key.contains("Год", ignoreCase = true) -> year = value.toIntOrNull()
            key.contains("Жанр", ignoreCase = true) -> genres = value.split(",").map { it.trim() }
            key.contains("Страна", ignoreCase = true) -> country = value
            key.contains("Режиссер", ignoreCase = true) -> director = value
            key.contains("В ролях", ignoreCase = true) -> actors = value
        }
    }

    // режиссёр как ActorData
    val directorActor = director?.let { listOf(ActorData(Actor(it, null))) } ?: emptyList()

    // актёры
    val actorList = actors?.split(",")?.map {
        ActorData(Actor(it.trim(), null))
    } ?: emptyList()

    val allActors = directorActor + actorList

    // определяем тип
    val type = when {
        url.contains("/series/") -> TvType.TvSeries
        url.contains("/animation/") -> TvType.Anime
        else -> TvType.Movie
    }

    return when (type) {
        TvType.Movie -> newMovieLoadResponse(title, url, type, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = fullPlot
            this.tags = genres?.plus(country ?: "")
            this.actors = allActors
        }

        TvType.Anime, TvType.TvSeries -> newTvSeriesLoadResponse(title, url, type, emptyList()) {
            this.posterUrl = poster
            this.year = year
            this.plot = fullPlot
            this.tags = genres?.plus(country ?: "")
            this.actors = allActors
        }

        else -> null
    }
}
}
