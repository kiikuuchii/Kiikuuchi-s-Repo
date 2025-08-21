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
import org.jsoup.Jsoup

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka-ua.org"
    override var name = "Rezka"
    override val hasMainPage = false
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val tmdbApiKey = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI4OTBhODY0YjliMGFiM2Q1ODE5YmMzNDI4OTZkNmRlNSIsIm5iZiI6MTc1NTUzODM5MS42MDksInN1YiI6IjY4YTM2M2Q3ZTM5ODkyY2Y5ODgwN2NkYyIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.BCAo_jgcK7RHHJoMxL8-EuH21FI3AwYDzBorB0KtJyA" // !!! сюда вставь свой ключ с https://www.themoviedb.org/settings/api

    /** Поиск на rezka */
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search&subaction=search&q=$query"
        val doc = app.get(url).document

        return doc.select("div.b-content__inline_item").mapNotNull {
            val title = it.selectFirst("div.b-content__inline_item-link a")?.text() ?: return@mapNotNull null
            val link = it.selectFirst("div.b-content__inline_item-link a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src") ?: ""

            val type = when {
                title.contains("аниме", ignoreCase = true) -> TvType.Anime
                title.contains("сезон", ignoreCase = true) -> TvType.TvSeries
                else -> TvType.Movie
            }

            // пробуем найти инфу на TMDb
            val tmdbData = getTmdbData(title)

        newMovieSearchResponse(
            title,
            link,
            type
        ) {
            this.posterUrl = tmdbData?.backdrop ?: tmdbData?.poster ?: poster
            this.year = tmdbData?.year
        }
      }
    }

    /** Загрузка страницы фильма */
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("div.b-post__title h1")?.text() ?: "Без названия"
        val poster = doc.selectFirst("div.b-sidecover img")?.attr("src") ?: ""
        val description = doc.selectFirst("div.b-post__description_text")?.text()
        val yearText = doc.select("table.b-post__info tr:contains(год)").select("td").text()
        val year = yearText.toIntOrNull()

        val type = when {
            doc.text().contains("аниме", ignoreCase = true) -> TvType.Anime
            doc.text().contains("сезон", ignoreCase = true) -> TvType.TvSeries
            else -> TvType.Movie
        }

        val tmdbData = getTmdbData(title)

        return newMovieLoadResponse(
            title,
            url,
            type,
            "$mainUrl$url"
        ) {
            this.posterUrl = tmdbData?.backdrop ?: tmdbData?.poster ?: poster
            this.year = tmdbData?.year ?: year
            this.plot = description
        }
    }

    /** Запрос в TMDb API */
    private suspend fun getTmdbData(title: String): TmdbData? {
        return try {
            val apiUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&language=ru-RU&query=${title}"
            val json = app.get(apiUrl).parsedSafe<TmdbSearch>() ?: return null
            val result = json.results.firstOrNull() ?: return null

            val poster = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            val backdrop = result.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
            val year = result.release_date?.take(4)?.toIntOrNull()
                ?: result.first_air_date?.take(4)?.toIntOrNull()

            TmdbData(poster, backdrop, year)
        } catch (e: Exception) {
            null
        }
    }

    /** Дата-класс для хранения данных из TMDb */
    data class TmdbData(
        val poster: String?,
        val backdrop: String?,
        val year: Int?
    )

    /** JSON модель ответа TMDb */
    data class TmdbSearch(
        val results: List<TmdbResult>
    )

    data class TmdbResult(
        val poster_path: String?,
        val backdrop_path: String?,
        val release_date: String?,
        val first_air_date: String?
    )
}