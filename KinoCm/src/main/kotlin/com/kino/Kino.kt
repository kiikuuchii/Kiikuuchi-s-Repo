package com.kino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Document
import java.net.URLEncoder

class KinoCm : MainAPI() {
    override var mainUrl = "https://kinojump.com"
    override var name = "KinoCm"
    override val hasMainPage = true
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA)

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/index.php?do=search&subaction=search&search_start=0&full_search=0&story=$q"
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document

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

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document

        val poster = doc.selectFirst("div.bslide__poster a")?.attr("href")
            ?.let { fixUrl(it) }
            ?: doc.selectFirst("div.bslide__poster img")?.attr("data-src")
            ?.let { fixUrl(it) }
            ?: doc.selectFirst("div.bslide__poster img")?.attr("src")
            ?.let { fixUrl(it) }

        val title = doc.selectFirst("h1.bslide__title")?.text()?.trim() ?: ""
        val description = doc.selectFirst("div.page__text.full-text")?.text()?.trim()
        val genreEls = doc.select("ul.bslide__text li:has(a[href*='/serials/']) a")
        val genres = genreEls.mapNotNull { it.text()?.trim() }.filter { it.isNotEmpty() }
        val yearText = doc.selectFirst("ul.bslide__text li:contains(Дата выхода:) a")?.text()
        val year = yearText?.toIntOrNull()
        val seasonsInfo = doc.selectFirst("ul.bslide__text li.line-ep")?.text()?.trim()
        val tvType = if (seasonsInfo != null) TvType.TvSeries else TvType.Movie

        if (tvType == TvType.Movie) {
            return newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = genres
            }
        } else {
            val episodes = mutableListOf<Episode>()
            val seasonsJson = getSeasonsJsonFromPage(doc)

            seasonsJson?.all?.forEach { seasonKey, episodesMap ->
                episodesMap.forEach { _, translationsMap ->
                    translationsMap.forEach { _, epData ->
                        val seasonNumber = seasonKey.toIntOrNull() ?: 1
                        val episodeNumber = epData.episode ?: 1

                        episodes.add(
                            newEpisode(epData.id.toString()) {
                                name = "Серия ${episodeNumber}"
                                this.season = seasonNumber
                                this.episode = episodeNumber
                            }
                        )
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, tvType, episodes = episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodeId = data.toIntOrNull() ?: return false
        val episodePageUrl = "https://kinojump.com/watch/$episodeId"
        
        val doc = app.get(episodePageUrl, headers = mapOf("User-Agent" to userAgent)).document
        
        val sourceUrl = doc.selectFirst("source")?.attr("src") ?: return false
        
        callback(
            newExtractorLink(
                source = "Kinojump",
                name = "Kinojump",
                url = sourceUrl
            ) {
                this.referer = episodePageUrl
            }
        )
        return true
    }

    private fun getSeasonsJsonFromPage(doc: Document): SeasonsMap? {
        val scriptContent = doc.select("script")
            .firstOrNull { it.html().contains("const fileList =") }
            ?.html()
            ?.substringAfter("const fileList =")
            ?.substringBeforeLast(";")
            ?.trim()
            ?: return null
        return tryParseJson<SeasonsMap>(scriptContent)
    }

    @Serializable
    data class SeasonsMap(val all: Map<String, Map<String, Map<String, EpData>>>)

    @Serializable
    data class EpData(
        val id: Int,
        val id_file: Int?,
        val seasons: Int?,
        val episode: Int?,
        val translation: String?,
        val id_translation: Int?,
        val quality: String?,
        val id_quality: Int?,
        val uhd: Boolean?,
        val poster: String = ""
    )
}