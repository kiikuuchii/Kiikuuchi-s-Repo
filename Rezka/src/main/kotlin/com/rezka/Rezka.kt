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

    val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
    val posterUrl = doc.selectFirst(".b-post__infopic img")?.absUrl("src")
    val description = doc.selectFirst("div.b-post__description")?.text()?.trim()

    // жанры и страна
    val genres = doc.select("table.b-post__info td:matchesOwn(Жанр) ~ td a")
        .map { it.text().trim() }
    val countries = doc.select("table.b-post__info td:matchesOwn(Страна) ~ td a")
        .map { it.text().trim() }

    // год
    val year = doc.select("table.b-post__info td:matchesOwn(год) ~ td a")
        .text()
        .toIntOrNull()

    // режиссеры
    val directors = doc.select("table.b-post__info td:matchesOwn(Режиссер) ~ td a")
    .map { link ->
        ActorData(
            Actor(
                name = link.text().trim(),
                image = null
            ),
            roleString = "Режиссёр"
        )
    }

    // актеры
    val actors = doc.select("table.b-post__info td:matchesOwn(В ролях актеры) ~ td a")
    .map { link ->
        ActorData(
            Actor(
                name = link.text().trim(),
                image = null
            ),
            roleString = "Актёр"
        )
    }

    // определяем тип
    val isAnime = genres.any { it.contains("аниме", true) } || url.contains("/anime", true)
    val isSeries = !isAnime && doc.select("#simple-episodes-list, .b-simple_episode__list, .b-simple_episodes__list").isNotEmpty()

    val type = when {
        isAnime -> TvType.Anime
        isSeries -> TvType.TvSeries
        else -> TvType.Movie
    }

    return when (type) {
        TvType.Movie -> newMovieLoadResponse(title, url, type, dataUrl = url) {
            this.posterUrl = posterUrl
            this.year = year
            this.plot = description
            this.tags = (genres + countries).ifEmpty { null }
            this.actors = (directors + actors).ifEmpty { null }
        }

        TvType.TvSeries -> {
            val eps = mutableListOf<Episode>()
            doc.select("#simple-episodes-list a, .b-simple_episode__list a, .b-simple_episodes__list a")
                .forEachIndexed { idx, a ->
                    val epName = a.text().trim()
                    val epLink = a.absUrl("href").ifEmpty { url }
                    eps.add(
                        newEpisode(epLink) {
                            this.name = if (epName.isNotEmpty()) epName else "Эпизод ${idx + 1}"
                            this.episode = idx + 1
                        }
                    )
                }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes = eps) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.tags = (genres + countries).ifEmpty { null }
                this.actors = (directors + actors).ifEmpty { null }
            }
        }

        TvType.Anime -> {
            val eps = mutableListOf<Episode>()
            doc.select("#simple-episodes-list a, .b-simple_episode__list a, .b-simple_episodes__list a")
                .forEachIndexed { idx, a ->
                    val epName = a.text().trim()
                    val epLink = a.absUrl("href").ifEmpty { url }
                    eps.add(
                        newEpisode(epLink) {
                            this.name = if (epName.isNotEmpty()) epName else "Эпизод ${idx + 1}"
                            this.episode = idx + 1
                        }
                    )
                }

            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes = eps) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.tags = (genres + countries).ifEmpty { null }
                this.actors = (directors + actors).ifEmpty { null }
            }
        }

        else -> null
      }
   }
}