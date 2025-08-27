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

    // достаём iframe и id фильма
    val iframe = doc.selectFirst("div.tabs-block__content.video-inside iframe")
    val iframeSrc = iframe?.attr("src") ?: ""
    val movieId = Regex("token_movie=(\\d+)").find(iframeSrc)?.groupValues?.getOrNull(1)

    val episodes = mutableListOf<Episode>()

    if (movieId != null) {
        val apiUrl = "https://cdn.yoserial.tv/api/movies/$movieId"
        val res = app.get(apiUrl).parsedSafe<com.google.gson.JsonObject>()

        if (res != null && res.has("seasons")) {
            val seasons = res.getAsJsonArray("seasons")

            var seasonNum = 1
            for (season in seasons) {
                val seasonObj = season.asJsonObject
                val episodesArray = seasonObj.getAsJsonArray("episodes")

                var epNum = 1
                for (ep in episodesArray) {
    val epObj = ep.asJsonObject

    val epTitle = epObj.get("title")?.asString
    val epId = epObj.get("id")?.asString
    val epNum = epObj.get("num")?.asInt   // ← тут Int
    val seasonNum = epObj.get("season")?.asInt // ← тут Int

    if (epId != null) {
    episodes.add(
        newEpisode(epId) {
            this.name = epTitle ?: "Эпизод ${epNum ?: 0}"
            this.season = seasonNum      // <- присваиваем полю объекта
            this.episode = epNum
        }
     )
   }
}
                seasonNum++
            }
        }
    }

    return newTvSeriesLoadResponse(
        name = title,
        url = url,
        type = TvType.TvSeries,
        episodes = episodes
    ) {
        this.posterUrl = poster
        this.plot = description
    }
  }
}