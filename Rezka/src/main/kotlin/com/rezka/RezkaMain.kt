package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

suspend fun MainAPI.loadRezkaMainPage(page: Int): HomePageResponse {
    val tmdbApiKey = "890a864b9b0ab3d5819bc342896d6de5"
    val tmdbApi = "https://api.themoviedb.org/3"

    val categories = listOf(
        "🎬 Фильмы" to "$mainUrl/page/$page/?filter=watching&genre=1",
        "📺 Сериалы" to "$mainUrl/page/$page/?filter=watching&genre=2",
        "🎞️ Мультфильмы" to "$mainUrl/page/$page/?filter=watching&genre=3",
        "🍥 Аниме" to "$mainUrl/page/$page/?filter=watching&genre=82",
    )

    return newHomePageResponse(
        categories.map { (title, url) ->
            val doc = app.get(url).document
            val items = doc.select(".b-content__inline_item").mapNotNull { element ->
                val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val name = element.selectFirst(".b-content__inline_item-link")?.text() ?: return@mapNotNull null
                val year = element.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()

                // --- ищем постер в TMDB ---
                val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
                val tmdbRes = app.get("$tmdbApi/search/multi?api_key=$tmdbApiKey&language=ru&query=$encoded")
                    .parsedSafe<Map<String, Any?>>()

                val results = tmdbRes?.get("results") as? List<Map<String, Any?>>
                val first = results?.firstOrNull()
                val posterPath = first?.get("poster_path") as? String
                val poster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

                val type = when (title) {
                    "🎬 Фильмы" -> TvType.Movie
                    "📺 Сериалы" -> TvType.TvSeries
                    "🎞️ Мультфильмы" -> TvType.Cartoon
                    "🍥 Аниме" -> TvType.Anime
                    else -> TvType.Movie
                }

                newMovieSearchResponse(name, href, type) {
                    this.posterUrl = poster // приоритет TMDB
                    this.year = year
                }
            }
            HomePageList(title, items)
        }
    )
}