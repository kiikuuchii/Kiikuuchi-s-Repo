package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*   // тут лежит ExtractorLink, Qualities, newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document

object RezkaExtractor {
    /**
     * Достаёт iframe ссылку (учитываем разные варианты)
     */
    suspend fun getIframeUrl(pageUrl: String): String? {
        val doc: Document = app.get(pageUrl).document

        return doc.selectFirst("iframe")?.attr("src")
            ?: doc.selectFirst("div#oframeplayer")?.attr("data-src")
            ?: doc.selectFirst("div#oframeplayer")?.attr("data-iframe")
    }
}