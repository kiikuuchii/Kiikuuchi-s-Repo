package com.kino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

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

        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Без названия"
        val poster = doc.selectFirst(".full__poster img")?.attr("src")
        val plot = doc.selectFirst(".full__text")?.text()?.trim()

        val year = doc.select("span:contains(Год выпуска:) a").text().toIntOrNull()

        val genres = doc.select("span:contains(Жанр:) a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }

        // Проверяем есть ли iframe с плеером (это значит видео доступно)
        val iframe = doc.selectFirst("iframe")?.attr("src")

        return if (url.contains("serial") || title.contains("сезон", ignoreCase = true)) {
            // Сериал (пока список серий не вынимаем, только общая инфа)
            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = emptyList()
            ) {
                posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        } else {
            // Фильм
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = iframe ?: url
            ) {
                posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        }
    }
}