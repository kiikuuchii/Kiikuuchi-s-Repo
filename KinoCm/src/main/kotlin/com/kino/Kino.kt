package com.kino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class KinoCm : MainAPI() {
    override var mainUrl = "https://kinojump.com"
    override var name = "KinoCm"
    override val hasMainPage = true
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA)

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/index.php?do=search&subaction=search&search_start=0&full_search=0&story=$q"
        val doc = app.get(url).document

        val items = doc.select("div.poster")
        return items.mapNotNull { element ->
            val titleEl = element.selectFirst("h3.poster__title a") ?: return@mapNotNull null
            val hrefRaw = titleEl.attr("href").ifBlank { return@mapNotNull null }
            val href = fixUrl(hrefRaw)
            val title = titleEl.text().trim().ifBlank { return@mapNotNull null }

            val imgEl = element.selectFirst("img")
            val posterRaw = imgEl?.attr("data-src") ?: imgEl?.attr("src")
            val poster = posterRaw?.let { fixUrl(it) }

            val subtitleLis = element.select("ul.poster__subtitle li")
            val year = subtitleLis.getOrNull(0)?.text()?.toIntOrNull()
            val typeText = subtitleLis.getOrNull(1)?.text()?.lowercase()

            val inferredType = when {
                typeText == null -> TvType.Movie
                typeText.contains("сериал") || typeText.contains("сериалы") -> TvType.TvSeries
                typeText.contains("аниме") -> TvType.Anime
                typeText.contains("мульт") -> TvType.Cartoon
                else -> TvType.Movie
            }

            // Возвращаем корректный SearchResponse в зависимости от типа
            if (inferredType == TvType.TvSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, href, inferredType) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
        }
    }
	
	override suspend fun load(url: String): LoadResponse {
    val doc = app.get(url).document

    // --- Постер ---
    val poster = doc.selectFirst("div.bslide__poster a")?.attr("href")?.let {
        if (it.startsWith("http")) it else "https://kinojump.com$it"
    } ?: doc.selectFirst("div.bslide__poster img")?.attr("data-src")?.let {
        if (it.startsWith("http")) it else "https://kinojump.com$it"
    } ?: doc.selectFirst("div.bslide__poster img")?.attr("src")?.let {
        if (it.startsWith("http")) it else "https://kinojump.com$it"
    }

    // --- Названия и описание ---
    val title = doc.selectFirst("h1.bslide__title")?.text()?.trim() ?: ""
    val originalTitle = doc.selectFirst("div.bslide__subtitle")?.text()?.trim()
    val description = doc.selectFirst("div.page__text.full-text")?.text()?.trim()

    // --- Жанры ---
    val genreEls = doc.select("ul.bslide__text li:has(a[href*='/serials/']) a")
    val genres = genreEls.map { it.text().trim() }.filter { it.isNotEmpty() }

    // --- Год ---
    val yearText = doc.selectFirst("ul.bslide__text li:contains(Дата выхода:) a")?.text()
    val year = yearText?.toIntOrNull()

    // --- Рейтинг ---
    val ratingKp = doc.selectFirst(".rating--kp")?.text()?.toFloatOrNull()
    val ratingImdb = doc.selectFirst(".rating--imdb")?.text()?.toFloatOrNull()

    // --- Определяем тип ---
    val seasonsInfo = doc.selectFirst("ul.bslide__text li.line-ep")?.text()?.trim()
    val tvType = if (seasonsInfo != null) TvType.TvSeries else TvType.Movie

    if (tvType == TvType.Movie) {
        return newMovieLoadResponse(title, url, tvType, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = genres
        }
    } else {
        // --- Сериалы через JSON fileList ---
        val episodes = mutableListOf<Episode>()

        // Парсим JSON с сезонами и сериями со страницы
        val seasonsJson = getSeasonsJsonFromPage(doc)

        seasonsJson?.all?.forEach { seasonKey, episodesMap ->
            episodesMap.forEach { episodeKey, translationsMap ->
                translationsMap.forEach { _, epData ->
                    val seasonNumber = seasonKey.toIntOrNull() ?: 1
                    val episodeNumber = epData.episode ?: 1
                    val videoUrl = "https://kinojump.com/watch/${epData.id}" // пример ссылки на эпизод

                    episodes.add(
                        newEpisode("${seasonKey}, ${episodeKey}, ${videoUrl}") {
                            name = "Серия ${episodeNumber}"
                            season = seasonNumber
                            episode = episodeNumber
                            posterUrl = epData.poster.ifEmpty { poster }
                            data = "${seasonKey}, ${episodeKey}, ${videoUrl}"
                        }
                    )
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, tvType, episodes = episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = genres
        }
    }
}

// --- Функция для парсинга JSON fileList со страницы ---
fun getSeasonsJsonFromPage(doc: org.jsoup.nodes.Document): SeasonsMap? {
    val scriptContent = doc.select("script")
        .firstOrNull { it.html().contains("const fileList =") }
        ?.html()
        ?.substringAfter("const fileList =")
        ?.substringBeforeLast(";")
        ?.trim()
        ?: return null

    return tryParseJson<SeasonsMap>(scriptContent)
}

// --- Модели данных ---
data class EpData(
    val id: Int,
    val id_file: Int?,
    val seasons: Int?,
    val episode: Int?,
    val translation: String?,
    val id_translation: Int?,
    val quality: String?,
    val id_quality: Int?,
    val uhd: Boolean?,
    val poster: String = ""
)

data class SeasonsMap(val all: Map<String, Map<String, Map<String, EpData>>>)

}
