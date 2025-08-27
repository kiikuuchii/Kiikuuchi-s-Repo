package com.kinojump

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.HomePageResponse

class Kinojump : MainAPI() {
    override var mainUrl = "https://kinojump.com"
    override var name = "Kinojump"
    override var lang = "ru"
    override val hasMainPage = true

    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search&subaction=search&story=$query"
        val doc = app.get(url).document

        return doc.select(".short-list .short-item").map { element ->
            val href = fixUrl(element.selectFirst("a")!!.attr("href"))
            val title = element.selectFirst(".short-title")!!.text()
            val poster = fixUrl(element.selectFirst(".short-img-holder img")!!.attr("src"))
            val year = element.selectFirst(".short-date")?.text()?.toIntOrNull()

            val baseType = when {
                href.contains("/anime/") -> {
                    if (title.contains("OVA", true) || title.contains("ОВА", true))
                        TvType.OVA else TvType.Anime
                }
                href.contains("/cartoon/") -> TvType.Cartoon
                href.contains("/series/") -> TvType.TvSeries
                else -> TvType.Movie
            }

            val episodic = baseType != TvType.Movie

            if (episodic) {
                newTvSeriesSearchResponse(title, href, baseType) {
                    this.posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, href, baseType) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.fstory-title")?.text() ?: return null
        val poster = document.selectFirst(".fstory-img img")?.attr("src")?.let { fixUrl(it) }
        val description = document.selectFirst(".fstory-content")?.text()

        val genres = document.select(".fstory-line:contains(Жанр) a")
            .map { it.text().trim() }

        val tags = genres.toMutableList()
        val year = document.selectFirst(".fstory-line:contains(Год) a")?.text()?.toIntOrNull()

        val isAnime = genres.any { it.equals("Аниме", ignoreCase = true) }
        val isCartoon = genres.any { it.equals("Мультфильм", ignoreCase = true) }
        val hasEpisodes = document.select(".seria-item").isNotEmpty()

        val contentType = when {
            isAnime -> TvType.Anime
            isCartoon -> TvType.Cartoon
            hasEpisodes -> TvType.TvSeries
            else -> TvType.Movie
        }

        val episodes = document.select(".seria-item").mapIndexed { index, el ->
            val epName = el.selectFirst(".seria-title")?.text()
            val href = fixUrl(el.selectFirst("a")?.attr("href") ?: url)
            newEpisode(href) {
                this.name = epName
                this.episode = index + 1
            }
        }

        return when (contentType) {
            TvType.Anime -> newAnimeLoadResponse(title, url, contentType, false) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                if (episodes.isNotEmpty()) {
                    addEpisodes(DubStatus.Subbed, episodes)
                }
            }
            TvType.Cartoon, TvType.TvSeries -> newTvSeriesLoadResponse(title, url, contentType, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
            else -> newMovieLoadResponse(title, url, contentType, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
        }
    }
	
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        return loadKinojumpMainPage(page)
    }
}