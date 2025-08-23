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

data class TmdbSearchResult(
    val id: Int?,
    val title: String?,
    val name: String?,
    val release_date: String?,
    val first_air_date: String?,
    val backdrop_path: String?,
    val poster_path: String?,
)

data class TmdbSearchResponse(
    val results: List<TmdbSearchResult> = emptyList()
)

data class TmdbImage(
    val file_path: String?,
    val vote_average: Double?
)

data class TmdbImagesResponse(
    val backdrops: List<TmdbImage> = emptyList()
)

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka.ag"
    override var name = "Rezka"
    override val hasMainPage = true
    override var lang = "ru"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon
    )

    // 🔍 поиск
    override suspend fun search(query: String): List<SearchResponse> {
    val url = "$mainUrl/search/?do=search&subaction=search&q=${URLEncoder.encode(query, StandardCharsets.UTF_8.toString())}"
    val doc = app.get(url).document

    return doc.select("div.b-content__inline_item").mapNotNull {
        val title = it.select("div.b-content__inline_item-link a").text()
        val href = it.select("div.b-content__inline_item-link a").attr("href")
        val poster = it.select("div.b-content__inline_item-cover img").attr("src")

        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }
}

    // 📥 загрузка страницы фильма/сериала
    override suspend fun load(url: String): LoadResponse {
        val doc: Document = app.get(url).document

        val title = doc.select("h1").text()
        val poster = doc.select("div.b-post__poster img").attr("src")
        val year = doc.select("table.b-post__info tr:contains(год) td:last-child").text().toIntOrNull()
        val description = doc.select("div.b-post__description_text").text()

        // 🔎 Определяем тип
        val contentType = when {
            url.contains("/cartoons/") -> TvType.Cartoon
            url.contains("/anime/") -> TvType.Anime
            url.contains("/series/") -> TvType.TvSeries
            else -> TvType.Movie
        }

        // 🗝️ TMDB API
        val tmdbApiKey = "890a864b9b0ab3d5819bc342896d6de5" // вставь свой ключ
        val tmdbSearchUrl = when (contentType) {
            TvType.Movie ->
                "https://api.themoviedb.org/3/search/movie?api_key=$tmdbApiKey&language=ru&query=${
                    URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                }"
            TvType.Anime, TvType.Cartoon, TvType.TvSeries ->
                "https://api.themoviedb.org/3/search/tv?api_key=$tmdbApiKey&language=ru&query=${
                    URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                }"
            else ->
                "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&language=ru&query=${
                    URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                }"
        }

        val tmdbResponse = app.get(tmdbSearchUrl).parsedSafe<TmdbSearchResponse>()
        val first = tmdbResponse?.results?.firstOrNull {
            val releaseYear = (it.release_date ?: it.first_air_date)?.take(4)?.toIntOrNull()
            releaseYear != null && year != null && kotlin.math.abs(releaseYear - year) <= 1
        } ?: tmdbResponse?.results?.firstOrNull()

        var backdropUrl: String? = first?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
        val posterUrl: String = first?.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } ?: poster

        // 🖼️ Получаем список задников
        if (first?.id != null) {
            val mediaType = if (contentType == TvType.Movie) "movie" else "tv"
            val imagesUrl = "https://api.themoviedb.org/3/$mediaType/${first.id}/images?api_key=$tmdbApiKey"
            val imagesResponse = app.get(imagesUrl).parsedSafe<TmdbImagesResponse>()
            val bestBackdrop = imagesResponse?.backdrops?.maxByOrNull { it.vote_average ?: 0.0 }
            if (bestBackdrop?.file_path != null) {
                backdropUrl = "https://image.tmdb.org/t/p/original${bestBackdrop.file_path}"
            }
        }

        // 📺 собираем эпизоды
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

        // 🔄 возвращаем результат
        return when (contentType) {
            TvType.Cartoon -> {
                if (episodes.isNotEmpty()) {
                    newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                        this.posterUrl = posterUrl
                        this.backgroundPosterUrl = backdropUrl ?: posterUrl
                        this.year = year
                        this.plot = description
                    }
                } else {
                    newMovieLoadResponse(title, url, TvType.Cartoon, url) {
                        this.posterUrl = posterUrl
                        this.backgroundPosterUrl = backdropUrl ?: posterUrl
                        this.year = year
                        this.plot = description
                    }
                }
            }

            TvType.Anime -> {
                if (episodes.isNotEmpty()) {
                    newAnimeLoadResponse(title, url, TvType.Anime) {
                        this.posterUrl = posterUrl
                        this.backgroundPosterUrl = backdropUrl ?: posterUrl
                        this.year = year
                        this.plot = description
                        addEpisodes(DubStatus.Subbed, episodes)
                    }
                } else {
                    newAnimeLoadResponse(title, url, TvType.Anime) {
                        this.posterUrl = posterUrl
                        this.backgroundPosterUrl = backdropUrl ?: posterUrl
                        this.year = year
                        this.plot = description
                    }
                }
            }

            TvType.TvSeries -> {
                if (episodes.isNotEmpty()) {
                    newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                        this.posterUrl = posterUrl
                        this.backgroundPosterUrl = backdropUrl ?: posterUrl
                        this.year = year
                        this.plot = description
                    }
                } else {
                    newMovieLoadResponse(title, url, TvType.Movie, url) {
                        this.posterUrl = posterUrl
                        this.backgroundPosterUrl = backdropUrl ?: posterUrl
                        this.year = year
                        this.plot = description
                    }
                }
            }

            else -> {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = posterUrl
                    this.backgroundPosterUrl = backdropUrl ?: posterUrl
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