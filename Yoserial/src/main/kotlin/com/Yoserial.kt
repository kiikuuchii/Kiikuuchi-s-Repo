package com.yoserial

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup

class Yoserial : MainAPI() {
    override var mainUrl = "https://yoserial.tv"
    override var name = "Yoserial"
    override val hasMainPage = true
    override var lang = "ru"
    override val hasChromecastSupport = true

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

        return doc.select("div.item.expand-link").mapNotNull { element ->
            val href = element.selectFirst("a.item__title")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("a.item__title")?.text() ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")

            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = fixUrlNull(poster)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        val poster = doc.selectFirst("div.page__poster img")?.attr("src")?.let {
            if (it.startsWith("http")) it else mainUrl + it
        }
        val description = doc.selectFirst("div.full-text[itemprop=description]")?.text()?.trim()
        val year = doc.selectFirst("ul.page__list span[itemprop=dateCreated]")?.text()?.toIntOrNull()
        val genres = doc.select("ul.page__list span[itemprop=genre] a").map { it.text() }

        val allEpisodes = mutableListOf<Episode>()

        doc.select("div[data-select=seasonType1] button[data-id]").forEach { seasonElement ->
            val seasonId = seasonElement.attr("data-id").toIntOrNull() ?: return@forEach
            val seasonName = seasonElement.text().trim()

            // Находим все серии, которые относятся к этому сезону
            // Селектор 'div[data-select="episodeType$seasonId"]' работает для динамической загрузки серий.
            // Примечание: Убедитесь, что `data-select` для серий соответствует формату на сайте.
            val episodesForSeason = doc.select("div[data-select=episodeType$seasonId] button[data-id]")

            episodesForSeason.forEach { episodeElement ->
                val episodeId = episodeElement.attr("data-id").toIntOrNull() ?: return@forEach
                val episodeName = episodeElement.text().trim()

                allEpisodes.add(
                    newEpisode(url) {
                        this.name = episodeName
                        this.season = seasonId
                        this.episode = episodeId
                    }
                )
            }
        }
        
        return newTvSeriesLoadResponse(
            name = title,
            url = url,
            type = TvType.TvSeries,
            episodes = allEpisodes
        ) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = genres
        }
    }
}