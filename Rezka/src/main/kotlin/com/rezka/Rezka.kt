package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.rezka.loadRezkaMainPage
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup

class Rezka : MainAPI() {
    override var mainUrl = "https://rezka-ua.org"
    override var name = "Rezka"
    override var lang = "ru"
    override val hasMainPage = true

    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?do=search&subaction=search&q=$query"
        val doc = app.get(url).document

        return doc.select(".b-content__inline_item").map { element ->
            val href = element.selectFirst("a")!!.attr("href")
            val title = element.selectFirst(".b-content__inline_item-link")!!.text()
            val poster = element.selectFirst("img")!!.attr("src")
            val year = element.selectFirst(".b-content__inline_item-link > div")
                ?.text()?.toIntOrNull()

            val baseType = when {
                href.contains("/anime/") -> {
                    if (title.contains("OVA", true) || title.contains("ОВА", true))
                        TvType.OVA else TvType.Anime
                }
                href.contains("/cartoons/") -> TvType.Cartoon
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

    val title = document.selectFirst("div.b-post__title h1")?.text() ?: return null
    val poster = document.selectFirst("div.b-sidecover img")?.attr("src")
    val description = document.selectFirst("div.b-post__description_text")?.text()
    val yearText = document
        .select("tr:has(td h2:matches((?i)Дата выхода)) td:eq(1) a")
        .text()
        .trim()
    val year = yearText.filter { it.isDigit() }.toIntOrNull()

    // --- Определяем тип по жанру, а не только по url ---
    val genresText = document.select("table.b-post__info tr:contains(Жанр:)").text().lowercase()
	val genres = document.select("tr:has(td h2:matches((?i)Жанр)) td:eq(1) a span[itemprop=genre]")
        .map { it.text().trim() }

    val isAnime = genres.any { it.equals("Аниме", ignoreCase = true) }
    val isCartoon = genres.any { it.equals("Мультфильм", ignoreCase = true) }
    val hasEpisodes = document.select(".b-simple_episode__item").isNotEmpty()

    val contentType = when {
        isAnime -> TvType.Anime
        isCartoon -> TvType.Cartoon
        hasEpisodes -> TvType.TvSeries
        else -> TvType.Movie
    }
	
	val tags = mutableListOf<String>()
    if (genresText.contains("аниме")) tags.add("Аниме")
    if (genresText.contains("мультфильм")) tags.add("Мультфильм")

    tags.addAll(genres)
	
	// --- Актёры ---
    val actors = document.select("div.persons-list-holder span.person-name-item[data-job=Актер], span.person-name-item[data-job=Актриса]")
        .mapNotNull { el ->
            val name = el.selectFirst("[itemprop=name]")?.text()?.trim()
            val photo = el.attr("data-photo")?.takeIf { it.isNotBlank() }
            if (name != null) ActorData(Actor(name, photo), roleString = "Актёр") else null
        }

    // --- Режиссёры ---
    val directors = document.select("div.persons-list-holder span.person-name-item[data-job=Режиссер]")
        .mapNotNull { el ->
            val name = el.selectFirst("[itemprop=name]")?.text()?.trim()
            val photo = el.attr("data-photo")?.takeIf { it.isNotBlank() }
            if (name != null) ActorData(Actor(name, photo), roleString = "Режиссёр") else null
        }

    // --- Общий список ---
    val people = actors + directors
	
	

    // --- Эпизоды ---
    val episodes = document.select(".b-simple_episode__item").mapIndexed { index, el ->
        val epName = el.selectFirst(".b-simple_episode__item-title")?.text()
        val href = el.selectFirst("a")?.attr("href") ?: url
        newEpisode(href) {
            this.name = epName
            this.episode = index + 1
        }
    }

    // --- Возвращаем правильный билдер ---
    return when (contentType) {
        TvType.Anime -> newAnimeLoadResponse(title, url, contentType, false) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
			this.actors = people
            if (episodes.isNotEmpty()) {
                addEpisodes(DubStatus.Subbed, episodes)
            }
        }
        TvType.Cartoon, TvType.TvSeries -> newTvSeriesLoadResponse(title, url, contentType, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
			this.actors = people
        }
        else -> newMovieLoadResponse(title, url, contentType, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
			this.actors = people
        }
    }
}

       override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return RezkaExtractor().getM3u8Links(data, callback)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return loadRezkaMainPage(page)
    }
}
