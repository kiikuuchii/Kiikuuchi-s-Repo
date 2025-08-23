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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka.ag"
    override var name = "Rezka"
    override val hasMainPage = false
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon, TvType.Anime)

    private val tmdbApiKey = "890a864b9b0ab3d5819bc342896d6de5"
    private val tmdbApi = "https://api.themoviedb.org/3"

    // =============== SEARCH ==================
    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "$tmdbApi/search/multi?api_key=$tmdbApiKey&language=ru&query=$encoded"

        val res = app.get(url).parsedSafe<Map<String, Any?>>()
        val results = res?.get("results") as? List<Map<String, Any?>> ?: return emptyList()

        return results.mapNotNull { item ->
            val mediaType = item["media_type"] as? String ?: return@mapNotNull null
            val id = (item["id"] as? Int)?.toString() ?: return@mapNotNull null
            val title = item["title"] as? String ?: item["name"] as? String ?: return@mapNotNull null
            val posterPath = item["poster_path"] as? String
            val posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
            val year = ((item["release_date"] ?: item["first_air_date"]) as? String)?.take(4)?.toIntOrNull()

            val type = when (mediaType) {
                "movie" -> TvType.Movie
                "tv" -> TvType.TvSeries
                else -> TvType.Movie
            }

            newMovieSearchResponse(title, id, type) {
                this.posterUrl = posterUrl
                this.year = year
            }
        }
    }

    // =============== LOAD ==================
    override suspend fun load(url: String): LoadResponse {
        // url = TMDB ID (строка)
        val id = url.toIntOrNull() ?: throw ErrorLoadingException("Bad ID")
        val tmdbDetails = app.get("$tmdbApi/movie/$id?api_key=$tmdbApiKey&language=ru").parsedSafe<Map<String, Any?>>()
            ?: app.get("$tmdbApi/tv/$id?api_key=$tmdbApiKey&language=ru").parsedSafe()

        val title = (tmdbDetails?.get("title") ?: tmdbDetails?.get("name")) as? String ?: "Без названия"
        val overview = tmdbDetails?.get("overview") as? String
        val posterPath = tmdbDetails?.get("poster_path") as? String
        val backdropPath = tmdbDetails?.get("backdrop_path") as? String
        val year = ((tmdbDetails?.get("release_date") ?: tmdbDetails?.get("first_air_date")) as? String)
            ?.take(4)?.toIntOrNull()

        val posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }

        // ==================== подтягиваем видео с Rezka ====================
        val rezkaUrl = "$mainUrl/search/?do=search&subaction=search&q=${URLEncoder.encode(title, "UTF-8")}"
        val rezkaDoc = app.get(rezkaUrl).document
        val firstResult = rezkaDoc.select("div.b-content__inline_item a").attr("href")

        val doc = app.get(firstResult).document
        val episodes = mutableListOf<Episode>()
        doc.select("div.b-simple_episode__item").forEach { ep ->
            val epName = ep.select("a").text()
            val link = ep.select("a").attr("href")
            val episodeNum = ep.select("div.number").text().toIntOrNull()
            episodes.add(
                newEpisode(link) {
                    this.name = epName
                    this.episode = episodeNum
                }
            )
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, firstResult, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl ?: posterUrl
                this.plot = overview
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, firstResult, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl ?: posterUrl
                this.plot = overview
                this.year = year
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return loadRezkaMainPage(page)
    }
}
