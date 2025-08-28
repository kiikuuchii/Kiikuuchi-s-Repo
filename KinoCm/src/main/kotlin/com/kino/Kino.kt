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

    // --------------------- LOAD ---------------------
    override suspend fun load(url: String): LoadResponse {
    val doc = app.get(url).document

    val title = doc.selectFirst("article .fullstory__title h1")?.text()?.trim() ?: "Без названия"
    val poster = doc.selectFirst(".movie_poster img")?.attr("src")?.trim()
    
    // --- Описание ---
    val plot = doc.selectFirst(".r-1:has(.rl-1:contains(Слоган)) .rl-3")?.text()?.trim()
    
    // --- Жанры ---
    val genres = doc.selectFirst(".r-1:has(.rl-1:contains(Жанр сериала)) .rl-3")
        ?.text()
        ?.split('/', '·', '|')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    // --- Год ---
    val year = doc.selectFirst(".m_info .d-flex:contains(Год выпуска)")?.select("a")?.firstOrNull()?.text()?.toIntOrNull()

    // --- Возвращаем правильный объект ---
    return newTvSeriesLoadResponse(
        name = title,
        url = url,
        type = TvType.TvSeries,
        episodes = emptyList()
    ) {
        posterUrl = poster
        this.plot = plot
        this.year = year
        this.tags = genres
        apiName = name
    }
  }
}