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
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.json.JSONObject
import java.net.URLEncoder
import org.jsoup.Jsoup

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka-ua.org"
    override var name = "Rezka"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "ru"
    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
    val url = "$mainUrl/index.php?do=search&subaction=search&q=$query"
    val response = app.get(url).text
    println("SEARCH DEBUG: $response") // временно для проверки
    val doc = Jsoup.parse(response)

    val results = doc.select("div.b-content__inline_item")
    println("FOUND ELEMENTS: ${results.size}")

    return results.mapNotNull { element ->
        val title = element.selectFirst("div.b-content__inline_item-link a")?.text()
        val link = element.selectFirst("div.b-content__inline_item-link a")?.attr("href")
        if (title != null && link != null) {
            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = element.selectFirst("div.b-content__inline_item-cover img")?.attr("src")
            }
        } else null
    }
}

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: "Без названия"
        val poster = doc.selectFirst("div.b-sidecover img")?.attr("src")
        val description = doc.selectFirst("div.b-post__description_text")?.text()
        val yearText = doc.select("table.b-post__info td:contains(год) + td").text()
        val year = yearText.toIntOrNull()

        val type = when {
            doc.select("table.b-post__info td:contains(жанр) + td").text().contains("аниме", ignoreCase = true) -> TvType.Anime
            doc.select("table.b-post__info td:contains(жанр) + td").text().contains("сериал", ignoreCase = true) -> TvType.TvSeries
            else -> TvType.Movie
        }

        return when (type) {
            TvType.Movie -> newMovieLoadResponse(title, url, type, url) {
                posterUrl = poster
                plot = description
                this.year = year
            }
            else -> newTvSeriesLoadResponse(title, url, type, episodes = listOf()) {
                posterUrl = poster
                plot = description
                this.year = year
            }
        }
    }
}