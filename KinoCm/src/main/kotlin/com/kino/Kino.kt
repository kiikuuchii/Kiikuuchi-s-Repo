package com.kino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class KinoCm : MainAPI() {
    override var mainUrl = "https://kino.cm"
    override var name = "Kino.cm"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // --------------------- SEARCH ---------------------
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "%20")}"
        val doc = app.get(url).document

        val items = doc.select("div.shortstory")
        return items.mapNotNull { el ->
            val title = el.selectFirst(".shortstory__title a h2")?.text()?.trim() ?: return@mapNotNull null
            val href = el.selectFirst(".shortstory__title a")?.attr("href") ?: return@mapNotNull null
            val poster = el.selectFirst(".shortstory__poster img")?.attr("data-src")?.let {
                if (it.startsWith("/")) "$mainUrl$it" else it
            }

            val year = el.select("span:contains(Год выпуска:) a").text().toIntOrNull()

            // Проверяем сериал или фильм
            val isSeries = title.contains("сезон", ignoreCase = true) || href.contains("serial", ignoreCase = true)

            if (isSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    posterUrl = poster
                    this.year = year
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
    val doc = app.get(url).document

    val title = doc.selectFirst("h1")?.text() ?: ""
    val description = doc.selectFirst("div.fullstory__text")?.text()
        ?: doc.select("div.r-1:contains(Слоган:) .rl-3").text()
    val poster = doc.selectFirst("div.fullstory__poster img")?.attr("src")
    val yearText = doc.select("div.r-1:contains(Год выпуска:) a").text()
    val year = yearText.toIntOrNull()

    val genres = doc.select("div.r-1:matchesOwn(Жанр)").select("a")
        .map { it.text() }
        .ifEmpty { listOf(doc.select("div.r-1:contains(Жанр сериала:) .rl-3").text()) }

    val isSeries = doc.select("div.r-1:contains(Жанр сериала:)").isNotEmpty()
	val episodesList = emptyList<Episode>() // пока пустой список

    return if (!isSeries) {
        newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = genres
        }
    } else {
        newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes = episodesList) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = genres
        }
      }
   }
}