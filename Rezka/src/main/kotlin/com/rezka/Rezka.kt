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
    override var mainUrl = "https://rezka-ua.org"
    override var name = "Rezka"
    override var lang = "ru"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA)

    override val hasMainPage = true

    private val tmdbApiKey = "890a864b9b0ab3d5819bc342896d6de5"

    override suspend fun search(query: String): List<SearchResponse> {
        // теперь ищем напрямую по TMDB
        val url =
            "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&language=ru&query=${
                URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            }"
        val tmdbResponse = app.get(url).parsedSafe<Map<String, Any>>()
        val results = tmdbResponse?.get("results") as? List<Map<String, Any>> ?: return emptyList()

        return results.mapNotNull { item ->
            val id = (item["id"] as? Int) ?: return@mapNotNull null
            val mediaType = item["media_type"] as? String ?: return@mapNotNull null
            val title = (item["title"] ?: item["name"]) as? String ?: return@mapNotNull null
            val poster = item["poster_path"] as? String
            val year = ((item["release_date"] ?: item["first_air_date"]) as? String)
                ?.take(4)?.toIntOrNull()

            val posterUrl = poster?.let { "https://image.tmdb.org/t/p/w500$it" }

            when (mediaType) {
                "movie" -> newMovieSearchResponse(title, "$mainUrl/tmdb/$id", TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.year = year
                }
                "tv" -> newTvSeriesSearchResponse(title, "$mainUrl/tmdb/$id", TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.year = year
                }
                else -> null
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        // если url содержит /tmdb/, значит грузим данные с TMDB
        return if (url.contains("/tmdb/")) {
            val id = url.substringAfterLast("/")
            val type = if (url.contains("movie")) "movie" else "tv"

            val tmdbUrl =
                "https://api.themoviedb.org/3/$type/$id?api_key=$tmdbApiKey&language=ru"
            val data = app.get(tmdbUrl).parsedSafe<Map<String, Any>>() ?: throw ErrorLoadingException("TMDB not found")

            val title = (data["title"] ?: data["name"]) as? String ?: "Без названия"
            val poster = (data["poster_path"] as? String)?.let { "https://image.tmdb.org/t/p/w500$it" }
            val backdrop = (data["backdrop_path"] as? String)?.let { "https://image.tmdb.org/t/p/original$it" }
            val year = ((data["release_date"] ?: data["first_air_date"]) as? String)?.take(4)?.toIntOrNull()
            val description = data["overview"] as? String

            // Серии грузим с Rezka по названию
            val rezkaSearchUrl = "$mainUrl/search/?do=search&subaction=search&q=${
                URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            }"
            val rezkaDoc = app.get(rezkaSearchUrl).document
            val rezkaLink = rezkaDoc.selectFirst(".b-content__inline_item a")?.attr("href")
            val episodes = mutableListOf<Episode>()

            if (rezkaLink != null) {
                val rezkaDocLoad = app.get(rezkaLink).document
                rezkaDocLoad.select("div.b-simple_episode__item").forEach { ep ->
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
            }

            if (type == "tv") {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.year = year
                    this.plot = description
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backdrop
                    this.year = year
                    this.plot = description
                }
            }
        } else {
            // fallback — стандартный Rezka load
            val doc = app.get(url).document
            val title = doc.select("h1").text()
            val poster = doc.select("div.b-post__poster img").attr("src")
            val year = doc.select("table.b-post__info tr:contains(год) td:last-child").text().toIntOrNull()
            val description = doc.select("div.b-post__description_text").text()

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return loadRezkaMainPage(page)
    }
}
