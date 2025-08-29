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
    
    // Формируем полный URL постера
    val posterSrc = doc.selectFirst("div.movie_poster img")?.attr("src")
    val poster = posterSrc?.let { if (it.startsWith("http")) it else "https://kino.cm$it" }

    val yearText = doc.select("div.r-1:contains(Год выпуска:) a").text()
    val year = yearText.toIntOrNull()

    val genres = doc.selectFirst(".r-1:has(.rl-1:contains(Жанр сериала)) .rl-3")
        ?.text()
        ?.split('/', '·', '|')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    val isSeries = doc.select("div.r-1:contains(Жанр сериала:)").isNotEmpty()
    val episodesList = doc.select("div.serial-series-box select option").map { ep ->
    val epTitle = ep.attr("data-title") // "1 серия", "2 серия" и т.д.
    val epNumber = ep.attr("value").toIntOrNull() ?: 0
    val epId = ep.attr("data-id") // id эпизода для запроса видео
    newEpisode(epId) {
        name = epTitle
        season = 1 // если на сайте пока только 1 сезон, можно так
        episode = epNumber
        posterUrl = poster // можно использовать постер сериала
    }
}

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