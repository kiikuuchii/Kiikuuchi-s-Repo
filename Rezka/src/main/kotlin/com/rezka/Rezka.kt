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
    override var name = "Rezka"
    override var mainUrl = "https://rezka-ua.org"
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search/?do=search&subaction=search&q=$query").document
        val items = doc.select(".b-content__inline_item")
        return items.mapNotNull { el ->
            val title = el.selectFirst(".b-content__inline_item-link")?.text() ?: return@mapNotNull null
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src")
            val year = el.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()

            val isSeries = href.contains("/series", true) || href.contains("serial", true)

            if (isSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries).apply {
                    posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie).apply {
                    posterUrl = poster
                    this.year = year
                }
            }
        }
    }

    // --------------------- LOAD ---------------------
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = (
            doc.selectFirst(".b-post__title h1")?.text()
                ?: doc.selectFirst("h1")?.text()
                ?: "Без названия"
        ).trim()

        val poster = doc.selectFirst(".b-post__img img")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.trim()

        val plot = doc.selectFirst(".b-post__description_text, .b-post__description")
            ?.text()
            ?.trim()

        val infoMap = mutableMapOf<String, String>()
        for (tr in doc.select(".b-post__info table tr")) {
            val key = tr.selectFirst("td.l")?.text()?.trim()?.removeSuffix(":") ?: continue
            val value = tr.select("td").getOrNull(1)?.text()?.trim() ?: continue
            infoMap[key] = value
        }

        val year = infoMap["Год"]?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
        val genres = infoMap["Жанр"]
            ?.split(',', '·', '|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val looksLikeSeries = doc.selectFirst(".b-simple_season__nav, .b-simple_episode__item, .b-episodes__list") != null ||
                url.contains("/series", ignoreCase = true) ||
                url.contains("serial", ignoreCase = true)

        return if (!looksLikeSeries) {
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = url
            ).apply {
                posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        } else {
            val episodes = emptyList<Episode>()
            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ).apply {
                posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        }
    }
}