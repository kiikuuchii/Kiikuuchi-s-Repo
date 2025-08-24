package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.json.JSONObject

object RezkaExtractor {
    suspend fun getUrls(pageUrl: String): List<ExtractorLink>? {
        val doc = app.get(pageUrl).document

        // --- iframe плеера ---
        val iframe = doc.selectFirst("iframe#iframe-player")?.attr("src")
            ?: return null

        // --- грузим сам iframe ---
        val iframeHtml = app.get(iframe, referer = pageUrl).text

        // В iframe есть JSON вида: "streams": {"1080p": "https://....m3u8", "720p": "..."}
        val regex = Regex("\"streams\"\\s*:\\s*\\{([^}]+)\\}")
        val match = regex.find(iframeHtml) ?: return null

        val jsonString = "{${match.groupValues[1]}}"
        val json = JSONObject(jsonString)

        val links = mutableListOf<ExtractorLink>()

        for (key in json.keys()) {
            val videoUrl = json.getString(key)
            val quality = when (key) {
                "1080p" -> Qualities.P1080.value
                "720p" -> Qualities.P720.value
                "480p" -> Qualities.P480.value
                "360p" -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }

            links.add(
                newExtractorLink(
                    source = "Rezka",
                    name = "Rezka $key",
                    url = videoUrl,
                    type = if (videoUrl.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.quality = quality
                    this.headers = mapOf("Referer" to iframe)
                }
            )
        }

        return if (links.isNotEmpty()) links else null
    }
}