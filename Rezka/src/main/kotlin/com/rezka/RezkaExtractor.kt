package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile

class RezkaExtractor : ExtractorApi() {
    override val name = "Rezka"
    override val mainUrl = "https://rezka.ag"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Загружаем страницу
        val doc = app.get(url, referer = referer).document

        // В HTML лежат прямые ссылки на .m3u8
        val regex = Regex("""https[^\s'"]+\.m3u8""")
        val matches = regex.findAll(doc.toString()).map { it.value }.toList()

        if (matches.isEmpty()) return

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to (referer ?: mainUrl)
        )

        matches.distinct().forEach { m3u8Url ->
            // Генерируем потоки с качествами
            M3u8Helper.generateM3u8(
                name,
                m3u8Url,
                mainUrl,
                headers = headers
            ).forEach { link ->
                callback(
                    newExtractorLink(
                        name = name,
                        source = name,
                        url = link.url,
                        type = link.type,
                    ) {
                        this.quality = link.quality ?: Qualities.P720.value
                    }
                )
            }
        }
    }
}