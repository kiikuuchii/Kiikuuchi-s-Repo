package com.rezka

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Rezka : MainAPI() {
    override var name = "Rezka"
    override var mainUrl = "https://rezka-ua.org"
    override var lang = "ru"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        // Пока тестовый результат, чтобы проверить, что плагин работает
        return listOf(
            newMovieSearchResponse(
                "Тестовый фильм",
                "https://rezka-ua.org/test",
                TvType.Movie
            ) {
                this.posterUrl = null
                this.year = 2025
            }
        )
    }

    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse("Тестовый фильм", url, TvType.Movie, url) {
            this.posterUrl = null
            this.year = 2025
            this.plot = "Это заглушка для проверки плагина."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return false
    }
}
