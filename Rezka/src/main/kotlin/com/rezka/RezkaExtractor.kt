package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

object RezkaExtractor {

    suspend fun getUrl(
        pageUrl: String,
        season: Int? = null,
        episode: Int? = null
    ): List<ExtractorLink>? {
        val doc = app.get(pageUrl).document

        val id = doc.selectFirst("#ctrl_holder")?.attr("data-id") ?: return null
        val translatorId = doc.selectFirst("#ctrl_holder")?.attr("data-translator_id") ?: return null

        val isSeries = doc.select(".b-simple_episode__item").isNotEmpty()

        val ajaxUrl = if (isSeries) {
            "https://rezka-ua.org/ajax/get_cdn_series/"
        } else {
            "https://rezka-ua.org/ajax/get_cdn_movies/"
        }

        val postData = mutableMapOf(
            "id" to id,
            "translator_id" to translatorId,
        )

        if (isSeries) {
            postData["season"] = season?.toString() ?: "1"
            postData["episode"] = episode?.toString() ?: "1"
            postData["action"] = "get_stream"
        } else {
            postData["action"] = "get_movie"
        }

        val response = app.post(
            ajaxUrl,
            data = postData,
            referer = pageUrl
        ).text

        val json = JSONObject(response)
        if (!json.has("url")) return null

        val qualities = json.optJSONObject("qualities") ?: return null
        val iframe = pageUrl

        val links = mutableListOf<ExtractorLink>()

        qualities.keys().forEach { quality ->
            val videoUrl = qualities.getString(quality)
            val q = when (quality) {
                "360p" -> Qualities.P360
                "480p" -> Qualities.P480
                "720p" -> Qualities.P720
                "1080p" -> Qualities.P1080
                else -> Qualities.Unknown
            }

            links.add(
    newExtractorLink(
        source = "Rezka",
        name = "Rezka $quality",
        url = videoUrl,
        type = if (videoUrl.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
    ) {
        this.quality = q.value  // <-- берём значение Int
        this.headers = mapOf("Referer" to iframe) // <-- вместо this.referer
    }
)
        }

        return links
    }
}