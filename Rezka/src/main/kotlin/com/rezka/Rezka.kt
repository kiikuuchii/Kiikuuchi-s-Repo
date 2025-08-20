package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse

@CloudstreamPlugin
class Rezka : MainAPI() {
    override var mainUrl = "https://rezka-ua.org"
    override var name = "RezkaUA"
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?q=$query"
        val doc = app.get(url).document
        val results = ArrayList<SearchResponse>()

        doc.select(".b-content__inline_item-link").forEach { element ->
            val title = element.text()
            val link = element.attr("href")
            val poster = element.parent()?.parent()?.selectFirst("img")?.attr("src")
            results.add(
                newMovieSearchResponse(
                    name = title,
                    url = link,
                    type = TvType.Movie
                ) {
                    posterUrl = poster
                    year = null
                }
            )
        }
        return results
    }
}