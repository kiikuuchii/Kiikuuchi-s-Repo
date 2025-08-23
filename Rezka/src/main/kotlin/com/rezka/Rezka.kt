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
import com.lagradost.cloudstream3.DubStatus
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

    val title = doc.select("h1").text()
    val poster = doc.select("div.b-post__poster img").attr("src")
    val year = doc.select("table.b-post__info tr:contains(год) td:last-child").text().toIntOrNull()
    val description = doc.select("div.b-post__description_text").text()

    val contentType = when {
        url.contains("/cartoons/") -> TvType.Cartoon
        url.contains("/anime/") -> TvType.Anime
        url.contains("/series/") -> TvType.TvSeries
        else -> TvType.Movie
    }

    // собираем список эпизодов
    val episodes = mutableListOf<Episode>()
    doc.select("div.b-simple_episode__item").forEach { ep ->
        val name = ep.select("a").text()
        val link = ep.select("a").attr("href")
        val episodeNum = ep.select("div.number").text().toIntOrNull()

        episodes.add(
            newEpisode(link) {
                this.name = name
                this.episode = episodeNum
            }
        )
    }

    return when (contentType) {
    TvType.Cartoon -> {
        if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Cartoon, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    TvType.Anime -> {
    if (episodes.isNotEmpty()) {
        newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            addEpisodes(DubStatus.Subbed, episodes)
        }
    } else {
        newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            }
        }
    }

    TvType.TvSeries -> {
        if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    else -> {
        newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
        }
    }
}
}

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // реализовано в RezkaMain.kt (extension-функция)
        return loadRezkaMainPage(page)
    }
}   