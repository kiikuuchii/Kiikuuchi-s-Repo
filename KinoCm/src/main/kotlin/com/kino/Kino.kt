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

        val title = doc.selectFirst(".fullstory__title h1, h1")?.text()?.trim()
            ?: throw ErrorLoadingException("Не найдено название")

        val posterRaw = doc.selectFirst(".movie__poster img, .movie_poster img, .full-img img")?.attr("src")
        val poster = posterRaw?.let { fixUrl(it) }

        val description = doc.selectFirst(".full-text, .fullstory__desc, .excerpt, .full__text")?.text()?.trim()

        // Информация
        var year: Int? = null
        var country: String? = null
        var duration: String? = null
        var premiere: String? = null
        var quality: String? = null
        var translation: String? = null
        var added: String? = null
        var imdb: String? = null
        val genres = mutableListOf<String>()

        val infoElements = doc.select(".m_info .d-flex, .m_info .nd-flex, .movie__info .d-flex, .movie__info .nd-flex")
        for (el in infoElements) {
            val b = el.selectFirst("b")
            val key = b?.text()?.trim()?.replace(":", "") ?: ""
            val value = el.ownText()?.trim()?.ifEmpty { el.text().replace(b?.text() ?: "", "").trim() }

            when {
                key.contains("Год", ignoreCase = true) -> {
                    value?.filter { it.isDigit() }?.take(4)?.toIntOrNull()?.let { year = it }
                }
                key.contains("Страна", ignoreCase = true) -> {
                    country = el.selectFirst("a")?.text()?.trim() ?: value
                }
                key.contains("Жанр", ignoreCase = true) -> {
                    el.select("a").forEach { a -> 
                        val g = a.text().trim()
                        if (g.isNotEmpty()) genres.add(g)
                    }
                }
                key.contains("Продолжительность", ignoreCase = true) -> {
                    duration = value
                }
                key.contains("Премьера", ignoreCase = true) -> {
                    premiere = value
                }
                key.contains("Качество", ignoreCase = true) -> {
                    quality = value
                }
                key.contains("Перевод", ignoreCase = true) -> {
                    translation = value
                }
                key.contains("Добавлено", ignoreCase = true) -> {
                    added = value
                }
            }
        }

        val kp = doc.selectFirst(".rates .kp")?.text()?.replace("KP:", "")?.trim()
        val imdbText = doc.selectFirst(".rates .imdb")?.text()?.replace("IMDb:", "")?.trim()
        imdb = imdbText ?: imdb

        val iframeRaw = doc.selectFirst("iframe[src*='cinemar'], iframe[src*='cinemar.cc'], iframe#film, iframe")?.attr("src")
        val embedUrl = iframeRaw?.let { 
            if (it.startsWith("//")) "https:$it" 
            else if (it.startsWith("/")) "${mainUrl.trimEnd('/')}$it" 
            else it 
        }

        val isSeries = title.contains("сезон", ignoreCase = true) || doc.select(".added, .movie__info .added, .m_info:contains(сезон)").isNotEmpty()

        val extraInfo = buildString {
            if (!country.isNullOrEmpty()) append("Страна: $country\n")
            if (year != null) append("Год: $year\n")
            if (!duration.isNullOrEmpty()) append("Продолжительность: $duration\n")
            if (!translation.isNullOrEmpty()) append("Перевод: $translation\n")
            if (!quality.isNullOrEmpty()) append("Качество: $quality\n")
            if (!premiere.isNullOrEmpty()) append("Премьера: $premiere\n")
            if (!added.isNullOrEmpty()) append("Добавлено: $added\n")
            if (!imdb.isNullOrEmpty()) append("IMDB: $imdb\n")
            if (genres.isNotEmpty()) append("Жанры: ${genres.joinToString(", ")}\n")
        }

        val fullPlot = listOfNotNull(description, if (extraInfo.isBlank()) null else extraInfo).joinToString("\n\n")

        return if (isSeries) {
            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = emptyList()
            ) {
                posterUrl = poster
                this.plot = fullPlot
                this.year = year
                this.tags = genres
                this.apiName = name
            }
        } else {
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = embedUrl ?: url
            ) {
                posterUrl = poster
                this.plot = fullPlot
                this.year = year
                this.tags = genres
                this.apiName = name
            }
        }
    }
}