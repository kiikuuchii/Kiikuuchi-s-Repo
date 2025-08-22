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
import org.jsoup.Jsoup

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka-ua.org"
    override var name = "Rezka"
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search&subaction=search&q=$query"
        val doc = app.get(url).document

        return doc.select(".b-content__inline_item").map { element ->
            val href = element.selectFirst("a")!!.attr("href")
            val title = element.selectFirst(".b-content__inline_item-link")!!.text()
            val poster = element.selectFirst("img")!!.attr("src")
            val year = element.selectFirst(".b-content__inline_item-link > div")
                ?.text()?.toIntOrNull()

            // Определяем тип: если ссылка ведет на /anime/, то это аниме
            val type = when {
                href.contains("/anime/") -> TvType.Anime
                href.contains("/series/") -> TvType.TvSeries
                else -> TvType.Movie
            }

            newMovieSearchResponse(title, href, type) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst(".b-post__title")!!.text()
        val poster = doc.selectFirst(".b-sidecover img")?.attr("src")
        val year = doc.select(".b-post__info li")
            .find { it.text().contains("год") }
            ?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val description = doc.selectFirst(".b-post__description_text")?.text()

        val actors = doc.select(".b-post__info a[href*=/actor/]").map {
            ActorData(Actor(it.text()))
        }

        // Определяем тип
        val isAnime = url.contains("/anime/")
        val isSeries = url.contains("/series/") || isAnime

        return if (isSeries) {
            val episodes = doc.select(".b-simple_episode__item").mapIndexed { index, el ->
                val name = el.selectFirst(".b-simple_episode__item-title")?.text()
                val href = el.selectFirst("a")?.attr("href") ?: url
                newEpisode(href) {
                    this.name = name
                    this.episode = index + 1
                }
            }

            newTvSeriesLoadResponse(
                title,
                url,
                if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.actors = actors
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.actors = actors
            }
        }
    }
}