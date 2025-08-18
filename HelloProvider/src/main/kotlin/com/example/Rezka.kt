package com.Rezka

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newAnimeSearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Rezka : MainAPI() {
    override var name = "Rezka-UA"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun search(query: String): List<SearchResponse>? {
        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        }

        // Ссылка на поиск
        val url = "https://rezka-ua.org/index.php?do=search&subaction=search&q=$encodedQuery"

        // Получаем HTML
        val html = app.get(url).text

        // Парсим через Jsoup
        val doc = Jsoup.parse(html)
        val items = doc.select(".b-content__inline_item") // карточки фильмов/сериалов

        return items.mapNotNull { element ->
            val link = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst(".b-content__inline_item-link")?.text() ?: "No title"
            val poster = element.selectFirst("img")?.attr("src") ?: ""
            val year = element.selectFirst(".b-content__inline_item-year")?.text()?.toIntOrNull()

            newAnimeSearchResponse(title, link, fix = false) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }
}
