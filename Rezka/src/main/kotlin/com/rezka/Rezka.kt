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
    override var mainUrl = "https://rezka-ua.org"
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = Jsoup.connect("$mainUrl/index.php?do=search&subaction=search&q=$query").get()
        val results = mutableListOf<SearchResponse>()

        for (el in doc.select(".b-content__inline_item")) {
            val title = el.selectFirst(".b-content__inline_item-link")?.text() ?: continue
            val url = el.selectFirst("a")?.attr("href") ?: continue
            val poster = el.selectFirst("img")?.attr("src")
            results.add(
                newMovieSearchResponse(title, url, TvType.Movie) {
                    this.posterUrl = poster
                }
            )
        }
        return results
    }
}