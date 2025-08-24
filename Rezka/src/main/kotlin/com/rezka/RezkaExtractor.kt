package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*   // тут лежит ExtractorLink, Qualities, newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class RezkaExtractor : ExtractorApi() {
    override val name = "Rezka"
    override val mainUrl = "https://rezka.ag"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val document = app.get(url).document

        val script = document.select("script:containsData(player = {)").html()
        if (script.isNullOrBlank()) return null

        val regex = Regex("\"(\\d+p)\"\\s*:\\s*\"(https[^\"]+)\"")
        val matches = regex.findAll(script).toList() // теперь это List<MatchResult>

        val links = matches.mapNotNull { match ->
            val qualityStr = match.groupValues[1]
            val link = match.groupValues[2]

            val quality = when (qualityStr) {
                "1080p" -> Qualities.P1080.value
                "720p" -> Qualities.P720.value
                "480p" -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            // suspend вызов теперь корректен
            newExtractorLink(
                source = name,
                name = "$name $qualityStr",
                url = link,
                type = ExtractorLinkType.M3U8
            ) {
                this.quality = quality
            }
        }

        return links.ifEmpty { null }
    }
}