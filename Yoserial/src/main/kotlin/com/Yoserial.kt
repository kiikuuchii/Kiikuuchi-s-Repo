package com.yoserial

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Yoserial : MainAPI() {
    override var mainUrl = "https://yoserial.tv"
    override var name = "Yoserial"
    override var lang = "ru"
    override val hasMainPage = false
    override val hasChromecastSupport = true

    // На сайте только сериалы/аниме/мультсериалы
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime, TvType.Cartoon)

    // --------------------- SEARCH ---------------------
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search"
        val doc = app.post(
            url,
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "0",
                "result_from" to "1",
                "story" to query
            ),
            referer = mainUrl
        ).document

        return doc.select("div.item.expand-link.grid-items__item").mapNotNull { el ->
            val a = el.selectFirst("a.item__title") ?: return@mapNotNull null
            val href = a.attr("href") ?: return@mapNotNull null
            val title = a.text()?.trim().orEmpty()
            val posterRaw = el.selectFirst("img")?.attr("src")
            val poster = fixUrlNull(posterRaw)

            // Тип заранее точно не определить — ставим TvSeries
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
    }

    // --------------------- LOAD (детальная страница) ---------------------
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = (doc.selectFirst("h1")?.text() ?: "Без названия").trim()

        val poster = doc.selectFirst("div.page__poster img")
            ?.attr("src")
            ?.let { if (it.startsWith("http")) it else fixUrl(it) }

        val description = doc.selectFirst("div.full-text[itemprop=description]")
            ?.text()
            ?.trim()

        val year = doc.selectFirst("ul.page__list span[itemprop=dateCreated]")
            ?.text()
            ?.trim()
            ?.toIntOrNull()

        val genres = doc.select("ul.page__list span[itemprop=genre] a")
            .mapNotNull { it.text()?.trim() }
            .filter { it.isNotEmpty() }

        // Определяем тип по жанрам
        val genresLower = genres.joinToString(" ").lowercase()
        val contentType = when {
            "аниме" in genresLower -> TvType.Anime
            "мульт" in genresLower -> TvType.Cartoon
            else -> TvType.TvSeries
        }

        // --- Список серий и сезонов с CDN API ---
        val id = Regex("/(\\d+)-").find(url)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Не удалось найти ID сериала в URL")

        val apiUrl = "https://cdn.yoserial.tv/api/movies/$id"
        val json = app.get(apiUrl, referer = url).text

        val episodes = parseEpisodesFromApi(json)

        return newTvSeriesLoadResponse(
            name = title,
            url = url,
            type = contentType,
            episodes = episodes
        ) {
            posterUrl = poster
            plot = description
            this.year = year
            tags = genres
        }
    }

    /**
     * Простейший разбор JSON-ответа без библиотек:
     * ожидается структура:
     * {
     *   "seasons":[
     *     {"season_number":S,"episodes":[{"id":EID,"episode_number":E,...}, ...]},
     *     ...
     *   ]
     * }
     */
    private fun parseEpisodesFromApi(json: String): List<Episode> {
        val out = ArrayList<Episode>()

        // Находим блоки сезонов
        val seasonRegex = Regex(
            "\\{\\s*\"season_number\"\\s*:\\s*(\\d+)\\s*,\\s*\"episodes\"\\s*:\\s*\\[(.*?)\\]",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        // Внутри сезона — эпизоды
        val episodeRegex = Regex(
            "\\{[^}]*?\"id\"\\s*:\\s*(\\d+)[^}]*?\"episode_number\"\\s*:\\s*(\\d+)[^}]*?(?:\"title\"\\s*:\\s*\"(.*?)\")?[^}]*?\\}",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        for (sMatch in seasonRegex.findAll(json)) {
            val seasonNum = sMatch.groupValues[1].toIntOrNull()
            val epsBlock = sMatch.groupValues[2]

            if (seasonNum != null) {
                for (eMatch in episodeRegex.findAll(epsBlock)) {
                    val epId = eMatch.groupValues.getOrNull(1)?.toIntOrNull()
                    val epNum = eMatch.groupValues.getOrNull(2)?.toIntOrNull()
                    val epTitle = eMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }

                    if (epId != null && epNum != null) {
                        out.add(
                            newEpisode(epId.toString()) {
                                name = epTitle ?: "Серия $epNum"
                                season = seasonNum
                                episode = epNum
                            }
                        )
                    }
                }
            }
        }

        return out
    }
}