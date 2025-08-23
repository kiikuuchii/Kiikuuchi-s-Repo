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
    override val hasMainPage = true
    override var lang = "ru"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Anime,
        TvType.AnimeMovie
    )

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

        // --- 🔑 Постеры и баннеры ---
        var backdropUrl: String? = null
        var posterUrl: String? = poster

        when (contentType) {
            TvType.Movie, TvType.TvSeries, TvType.AnimeMovie -> {
                // TMDB для фильмов и сериалов
                val tmdbApiKey = "890a864b9b0ab3d5819bc342896d6de5"
                val tmdbSearchUrl =
                    if (contentType == TvType.Movie || contentType == TvType.AnimeMovie)
                        "https://api.themoviedb.org/3/search/movie?api_key=$tmdbApiKey&language=ru&query=${
                            URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        }"
                    else
                        "https://api.themoviedb.org/3/search/tv?api_key=$tmdbApiKey&language=ru&query=${
                            URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        }"

                val tmdbResponse = app.get(tmdbSearchUrl).parsedSafe<Map<String, Any>>()
                val results = tmdbResponse?.get("results") as? List<Map<String, Any>>
                val first = results?.firstOrNull()
                val backdrop = first?.get("backdrop_path") as? String
                val posterTmdb = first?.get("poster_path") as? String

                backdropUrl = backdrop?.let { "https://image.tmdb.org/t/p/original$it" }
                posterUrl = posterTmdb?.let { "https://image.tmdb.org/t/p/w500$it" } ?: poster
            }

            TvType.Anime -> {
                // Shikimori для аниме
                val shikiUrl =
                    "https://shikimori.one/api/animes?search=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
                val shikiResp = app.get(shikiUrl).parsedSafe<List<Map<String, Any>>>()
                val first = shikiResp?.firstOrNull()
                val image = (first?.get("image") as? Map<*, *>)
                posterUrl = (image?.get("preview") as? String)?.let { "https://shikimori.one$it" } ?: poster
                backdropUrl = (image?.get("original") as? String)?.let { "https://shikimori.one$it" }
            }

            TvType.Cartoon -> {
                // Fanart для мультов
                val fanartApiKey = "YOUR_FANART_KEY_HERE" // 🔑 вставь сюда API ключ с fanart.tv
                val fanartUrl =
                    "https://webservice.fanart.tv/v3/movies/${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}?api_key=$fanartApiKey"

                val fanartResp = app.get(fanartUrl).parsedSafe<Map<String, Any>>()
                val movieBackgrounds = fanartResp?.get("moviebackground") as? List<Map<String, Any>>
                val moviePosters = fanartResp?.get("movieposter") as? List<Map<String, Any>>

                backdropUrl = movieBackgrounds?.firstOrNull()?.get("url") as? String
                posterUrl = moviePosters?.firstOrNull()?.get("url") as? String ?: poster
            }

            else -> {
                backdropUrl = poster
                posterUrl = poster
            }
        }

        // --- 🔑 Эпизоды ---
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
            TvType.TvSeries, TvType.Anime, TvType.Cartoon -> {
                newTvSeriesLoadResponse(title, url, contentType, episodes) {
                    this.posterUrl = posterUrl
                    this.backgroundPosterUrl = backdropUrl ?: posterUrl
                    this.plot = description
                    this.year = year
                }
            }
            else -> {
                newMovieLoadResponse(title, url, contentType, url) {
                    this.posterUrl = posterUrl
                    this.backgroundPosterUrl = backdropUrl ?: posterUrl
                    this.plot = description
                    this.year = year
                }
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return loadRezkaMainPage(page)
    }
} 