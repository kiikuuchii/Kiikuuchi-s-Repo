package com.kino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Kino : MainAPI() {
    override var mainUrl = "https://kino.cm"
    override var name = "Kino.cm"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "ru"
    override val hasMainPage = true

    // --------------------- SEARCH ---------------------
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search&subaction=search&story=$query"
        val doc = app.get(url).document

        val items = doc.select(".shortstory").mapNotNull { el ->
            val title = el.selectFirst(".shorttitle a")?.text()?.trim() ?: return@mapNotNull null
            val href = el.selectFirst(".shorttitle a")?.attr("href") ?: return@mapNotNull null
            val poster = el.selectFirst(".shortimg img")?.attr("src")

            // Определяем тип (сериал или фильм)
            val isSeries = href.contains("/serial", true) || href.contains("/series", true)

            if (isSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries).apply {
                    posterUrl = fixUrlNull(poster)
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie).apply {
                    posterUrl = fixUrlNull(poster)
                }
            }
        }

        return items
    }

    // --------------------- LOAD ---------------------
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Без названия"
        val poster = doc.selectFirst(".fposter img")?.attr("src")
        val plot = doc.selectFirst(".fdesc")?.text()?.trim()

        val year = doc.selectFirst(".finfo:contains(Год)")?.ownText()
            ?.filter { it.isDigit() }?.take(4)?.toIntOrNull()

        val genres = doc.select(".finfo:contains(Жанр) a")
            .map { it.text().trim() }

        // iframe с плеером
        val iframe = doc.selectFirst("iframe")?.attr("src")
            ?: throw ErrorLoadingException("Iframe not found")
        val embedUrl = fixUrl(iframe)

        val looksLikeSeries = embedUrl.contains("tv-series")

        return if (looksLikeSeries) {
            // пока без парсинга серий, только заглушка
            val episodes = emptyList<Episode>()
            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ).apply {
                posterUrl = fixUrlNull(poster)
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = embedUrl
            ).apply {
                posterUrl = fixUrlNull(poster)
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        }
    }
}
