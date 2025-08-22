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

    val title = doc.selectFirst(".b-post__title h1")?.text()?.trim() ?: return null
    val posterUrl = doc.selectFirst(".b-sidecover img")?.attr("src")
    val description = doc.selectFirst(".b-post__description_text")?.text()?.trim()

    var year: Int? = null
    val genres = mutableListOf<String>()
    val countries = mutableListOf<String>()
    val actors = mutableListOf<ActorData>()

    doc.select("table.b-post__info tr").forEach { tr ->
        val key = (tr.selectFirst("td:nth-child(1)") ?: tr.selectFirst("th"))?.text()?.trim().orEmpty()
        val valueCell = tr.selectFirst("td:nth-child(2)") ?: tr.selectFirst("td")
        when {
            key.contains("Год", true) -> {
                year = valueCell?.text()?.let { Regex("(19|20)\\d{2}").find(it)?.value?.toIntOrNull() }
            }
            key.contains("Жанр", true) -> {
                valueCell?.select("a")?.forEach { a ->
                    val g = a.text().trim()
                    if (g.isNotEmpty()) genres.add(g)
                }
            }
            key.contains("Страна", true) -> {
                valueCell?.select("a")?.forEach { a ->
                    val c = a.text().trim()
                    if (c.isNotEmpty()) countries.add(c)
                }
            }
            key.contains("Режиссер", true) -> {
                valueCell?.select("a")?.forEach { a ->
                    val n = a.text().trim()
                    if (n.isNotEmpty()) actors.add(ActorData(Actor(n), role = null))
                }
            }
            key.contains("В ролях", true) -> {
                valueCell?.select("a")?.forEach { a ->
                    val n = a.text().trim()
                    if (n.isNotEmpty()) actors.add(ActorData(Actor(n), role = null))
                }
            }
        }
    }

    // --- Доп. проверка: если год всё ещё null, пробуем вытащить из заголовка (обычно "Название (2023)")
    if (year == null) {
        val titleH2 = doc.selectFirst(".b-post__title h2")?.text().orEmpty()
        year = Regex("(19|20)\\d{2}").find(titleH2)?.value?.toIntOrNull()
    }

    val isAnime = genres.any { it.contains("аниме", true) } || url.contains("/anime", true)
    val isSeries = doc.select("#simple-episodes-list, .b-simple_episode__list, .b-simple_episodes__list").isNotEmpty()

    val type = when {
        isAnime -> TvType.Anime   // приоритет за аниме
        isSeries -> TvType.TvSeries
        else -> TvType.Movie
    }

    return when (type) {
        TvType.Movie -> newMovieLoadResponse(title, url, type, dataUrl = url) {
            this.posterUrl = posterUrl
            this.year = year
            this.plot = description
            this.tags = (genres + countries).ifEmpty { null }
            this.actors = actors.ifEmpty { null }
        }

        TvType.TvSeries, TvType.Anime -> {
            val eps = mutableListOf<Episode>()
            doc.select("#simple-episodes-list a, .b-simple_episode__list a, .b-simple_episodes__list a").forEachIndexed { idx, a ->
                val epName = a.text().trim()
                val epLink = a.absUrl("href").ifEmpty { url }
                eps.add(
                    newEpisode(epLink) {
                        this.name = if (epName.isNotEmpty()) epName else "Эпизод ${idx + 1}"
                        this.episode = idx + 1
                    }
                )
            }

            newTvSeriesLoadResponse(title, url, type, episodes = eps) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = description
                this.tags = (genres + countries).ifEmpty { null }
                this.actors = actors.ifEmpty { null }
            }
        }

        else -> null
      }
   }
}