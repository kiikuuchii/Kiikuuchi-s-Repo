package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.ExtractorLinkType

object RezkaExtractor {
    suspend fun getUrl(iframeUrl: String): List<ExtractorLink>? {
        val response = app.get(iframeUrl).document
        val script = response.select("script:containsData(m3u8)").firstOrNull()?.data()
            ?: return null

        val regex = Regex("https[^'\"]+\\.m3u8")
        val m3u8Url = regex.find(script)?.value ?: return null

        return M3u8Helper.generateM3u8(
            "Rezka",
            m3u8Url,
            iframeUrl,
            Qualities.Unknown.value,
            headers = mapOf("Referer" to iframeUrl)
        )
    }
}
