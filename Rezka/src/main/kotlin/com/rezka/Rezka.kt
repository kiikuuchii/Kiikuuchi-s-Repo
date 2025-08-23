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
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Document
import com.rezka.loadRezkaMainPage
import org.jsoup.Jsoup

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka-ua.org"
    override var name = "Rezka"
    override var lang = "ru"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA)

    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?do=search&subaction=search&q=$query"
        val doc = app.get(url).document

        return doc.select(".b-content__inline_item").map { element ->
            val href = element.selectFirst("a")!!.attr("href")
            val title = element.selectFirst(".b-content__inline_item-link")!!.text()
            val poster = element.selectFirst("img")!!.attr("src")
            val year = element.selectFirst(".b-content__inline_item-link > div")
                ?.text()?.toIntOrNull()

            // определяем тип из ссылки + быстрая эвристика OVA по названию
            val baseType = when {
                href.contains("/anime/") -> {
                    if (title.contains("OVA", ignoreCase = true) || title.contains("ОВА", ignoreCase = true))
                        TvType.OVA else TvType.Anime
                }
                href.contains("/cartoons/") -> TvType.Cartoon
                href.contains("/series/") -> TvType.TvSeries
                else -> TvType.Movie
            }

            // В поиске безопасно отдавать TvSeriesSearch для потенциально эпизодных типов
            val episodic = baseType == TvType.TvSeries || baseType == TvType.Anime ||
                           baseType == TvType.OVA || baseType == TvType.Cartoon

            if (episodic) {
                newTvSeriesSearchResponse(title, href, baseType) {
                    this.posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, href, baseType) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst(".b-post__title")!!.text()
        val poster = doc.selectFirst(".b-sidecover img")?.attr("src")
        val year = doc.select(".b-post__info li")
            .find { it.text().contains("год", ignoreCase = true) }
            ?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val description = doc.selectFirst(".b-post__description_text")?.text()

        // секции сайта
        val isAnimeSection = url.contains("/anime/")
        val isCartoonSection = url.contains("/cartoons/")
        val isSeriesSection = url.contains("/series/")

        // попытка распознать OVA у аниме
        val infoText = doc.select(".b-post__info li").joinToString(" | ") { it.text().lowercase() }
        val isOva = isAnimeSection && (
            infoText.contains("ova") ||
            infoText.contains("оva") || // на всякий случай
            infoText.contains("ова") ||
            title.contains("OVA", ignoreCase = true) ||
            title.contains("ОВА", ignoreCase = true)
        )

        val contentType = when {
            isOva -> TvType.OVA
            isAnimeSection -> TvType.Anime
            isCartoonSection -> TvType.Cartoon
            isSeriesSection -> TvType.TvSeries
            else -> TvType.Movie
        }

        val hasEpisodes = doc.select(".b-simple_episode__item").isNotEmpty()

        return if (hasEpisodes) {
            val episodes = doc.select(".b-simple_episode__item").mapIndexed { index, el ->
                val epName = el.selectFirst(".b-simple_episode__item-title")?.text()
                val href = el.selectFirst("a")?.attr("href") ?: url
                newEpisode(href) {
                    this.name = epName
                    this.episode = index + 1
                }
            }

            newTvSeriesLoadResponse(
                title,
                url,
                contentType, // тут уже НЕ TvSeries для аниме/ова/мультов
                episodes
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                contentType, // фильм-аниме → Anime, фильм-мультфильм → Cartoon и т.д.
                url
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // реализовано в RezkaMain.kt (extension-функция)
        return loadRezkaMainPage(page)
    }
}