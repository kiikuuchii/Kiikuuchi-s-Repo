package com.kino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class KinoCm : MainAPI() {
    override var mainUrl = "https://kinojump.com"
    override var name = "KinoCm"
    override val hasMainPage = true
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA)

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/index.php?do=search&subaction=search&search_start=0&full_search=0&story=$q"
        val doc = app.get(url).document

        val items = doc.select("div.poster")
        return items.mapNotNull { element ->
            val titleEl = element.selectFirst("h3.poster__title a") ?: return@mapNotNull null
            val hrefRaw = titleEl.attr("href").ifBlank { return@mapNotNull null }
            val href = fixUrl(hrefRaw)
            val title = titleEl.text().trim().ifBlank { return@mapNotNull null }

            val imgEl = element.selectFirst("img")
            val posterRaw = imgEl?.attr("data-src") ?: imgEl?.attr("src")
            val poster = posterRaw?.let { fixUrl(it) }

            val subtitleLis = element.select("ul.poster__subtitle li")
            val year = subtitleLis.getOrNull(0)?.text()?.toIntOrNull()
            val typeText = subtitleLis.getOrNull(1)?.text()?.lowercase()

            val inferredType = when {
                typeText == null -> TvType.Movie
                typeText.contains("сериал") || typeText.contains("сериалы") -> TvType.TvSeries
                typeText.contains("аниме") -> TvType.Anime
                typeText.contains("мульт") -> TvType.Cartoon
                else -> TvType.Movie
            }

            // Возвращаем корректный SearchResponse в зависимости от типа
            if (inferredType == TvType.TvSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, href, inferredType) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
        }
    }
}