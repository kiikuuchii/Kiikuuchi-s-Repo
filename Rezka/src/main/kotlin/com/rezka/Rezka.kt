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
    override var name = "Rezka"
    override var mainUrl = "https://rezka.ag"
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    //Поиск фильмов/сериалов
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?do=search&subaction=search&q=${query.replace(" ", "+")}"
        val doc = app.get(url).document

        val items = doc.select(".b-content__inline_item")
        return items.map { el ->
            val title = el.selectFirst(".b-content__inline_item-link")?.text() ?: "Без названия"
            val href = el.selectFirst("a")?.attr("href") ?: return@map null
            val poster = el.selectFirst("img")?.attr("src")
            val year = el.selectFirst(".b-content__inline_item-link > div")?.text()?.toIntOrNull()

            newMovieSearchResponse(
                name = title,
                url = href,
                type = TvType.Movie
            ).apply {
                this.posterUrl = poster
                this.year = year
            }
        }.filterNotNull()
    }

    //Пока заглушка для открытия фильма
    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse(
            name = "Test Load",
            url = url,
            type = TvType.Movie,
            dataUrl = url
        )
    }
}