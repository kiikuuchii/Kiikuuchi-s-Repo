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
    override val hasMainPage = false

    private val tmdbApiKey = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI4OTBhODY0YjliMGFiM2Q1ODE5YmMzNDI4OTZkNmRlNSIsIm5iZiI6MTc1NTUzODM5MS42MDksInN1YiI6IjY4YTM2M2Q3ZTM5ODkyY2Y5ODgwN2NkYyIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.BCAo_jgcK7RHHJoMxL8-EuH21FI3AwYDzBorB0KtJyA" // свой ключ TMDB
    private val tmdbBase = "https://api.themoviedb.org/3"

    private suspend fun getTmdbData(query: String, type: TvType): Pair<String?, Int?> {
        val searchType = if (type == TvType.Movie) "movie" else "tv"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$tmdbBase/search/$searchType?api_key=$tmdbApiKey&language=ru-RU&query=$encoded"
        return try {
            val json = JSONObject(app.get(url).text)
            val result = json.getJSONArray("results").optJSONObject(0) ?: return null to null
            val backdrop = result.optString("backdrop_path")?.takeIf { it.isNotBlank() }
            val year = (result.optString("release_date")
                ?: result.optString("first_air_date"))
                .takeIf { it.isNotBlank() }
                ?.substring(0, 4)
                ?.toIntOrNull()
            val fullPoster = backdrop?.let { "https://image.tmdb.org/t/p/w780$it" }
            fullPoster to year
        } catch (e: Exception) {
            null to null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search&subaction=search&q=$query"
        val doc = app.get(url).document

        return doc.select("div.b-content__inline_item").mapNotNull { element ->
            val title = element.selectFirst("div.b-content__inline_item-link > a")?.text() ?: return@mapNotNull null
            val link = element.selectFirst("div.b-content__inline_item-link > a")?.attr("href") ?: return@mapNotNull null
            val posterRezka = element.selectFirst("img")?.attr("src") ?: ""

            val type = when {
                title.contains("аниме", true) -> TvType.Anime
                title.contains("сериал", true) -> TvType.TvSeries
                else -> TvType.Movie
            }

            val (tmdbPoster, tmdbYear) = getTmdbData(title, type)

            val finalPoster = tmdbPoster ?: posterRezka.ifEmpty {
                "https://via.placeholder.com/300x450.png?text=No+Image"
            }

            newMovieSearchResponse(title, link, type) {
                this.posterUrl = finalPoster
                this.year = tmdbYear
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: return null
        val description = doc.selectFirst("div.b-post__description_text")?.text()
        val posterRezka = doc.selectFirst("div.b-sidecover img")?.attr("src") ?: ""
        val yearRezka = doc.select("table.b-post__info tr").find {
            it.text().contains("год", true)
        }?.select("td.last")?.text()?.toIntOrNull()

        val type = when {
            doc.text().contains("аниме", true) -> TvType.Anime
            doc.text().contains("сериал", true) -> TvType.TvSeries
            else -> TvType.Movie
        }

        val (tmdbPoster, tmdbYear) = getTmdbData(title, type)

        val finalPoster = tmdbPoster ?: posterRezka.ifEmpty {
            "https://via.placeholder.com/300x450.png?text=No+Image"
        }

        return when (type) {
            TvType.Movie -> newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = finalPoster
                this.year = tmdbYear ?: yearRezka
                this.plot = description
            }
            TvType.TvSeries, TvType.Anime -> newTvSeriesLoadResponse(title, url, type, emptyList()) {
                this.posterUrl = finalPoster
                this.year = tmdbYear ?: yearRezka
                this.plot = description
            }
            else -> null
        }
    }
}