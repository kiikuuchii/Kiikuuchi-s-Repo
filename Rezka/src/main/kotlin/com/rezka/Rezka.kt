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

    override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val title = document.selectFirst("div.b-post__title h1")?.text()?.trim()
        ?: throw ErrorLoadingException("Не удалось найти заголовок")

    val poster = document.selectFirst("div.b-sidecover img")?.attr("src")
    val plot = document.selectFirst("div.b-post__description_text")?.text()?.trim()

    val infoTable = document.selectFirst("table.b-post__info")

    var year: Int? = null
    var country: String? = null
    var genres: List<String>? = null
    val actors = mutableListOf<ActorData>()

    infoTable?.select("tr")?.forEach { row ->
        val key = row.selectFirst("td.l")?.text()?.trim() ?: return@forEach
        val valueCell = row.selectFirst("td:not(.l)")

        when {
            key.contains("Год", true) -> {
                year = valueCell?.text()?.trim()?.toIntOrNull()
            }
            key.contains("Жанр", true) -> {
                genres = valueCell?.select("a")?.map { it.text() }
            }
            key.contains("Страна", true) -> {
                country = valueCell?.text()?.trim()
            }
            key.contains("Режиссер", true) -> {
                valueCell?.select("a")?.forEach {
                    actors.add(ActorData(Actor(it.text())))
                }
            }
            key.contains("В ролях", true) -> {
                valueCell?.select("a")?.forEach {
                    actors.add(ActorData(Actor(it.text())))
                }
            }
        }
    }

    val isTvSeries = document.select("ul#simple-episodes-tabs").isNotEmpty()

    return if (isTvSeries) {
        newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = buildList {
                genres?.let { addAll(it) }
                country?.let { add(it) }
            }
            this.actors = actors
        }
    } else {
        newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = buildList {
                genres?.let { addAll(it) }
                country?.let { add(it) }
            }
            this.actors = actors
        }
    }
}
}
