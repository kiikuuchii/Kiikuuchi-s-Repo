package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class RezkaExtractor {
    suspend fun loadLinks(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return loadFromSegments(pageUrl, callback)
    }

    private suspend fun loadFromSegments(
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(pageUrl).document
        val scriptText = doc.select("script").joinToString("\n") { it.data() ?: "" }

        // ищем .ts сегменты
        val regex = Regex("""https://[^\s"']+?\.mp4:hls:seg-\d+-v\d+-a\d+\.ts""")
        val match = regex.find(scriptText) ?: return false

        // убираем ":hls:seg..." → получаем прямой mp4
        val segUrl = match.value
        val directUrl = segUrl.substringBefore(":hls:")

        // создаём ExtractorLink
        val link = newExtractorLink(
            source = "Rezka",
            name = "Rezka [MP4]",
            url = directUrl,
            type = ExtractorLinkType.VIDEO
        ) {
            this.quality = 1080
            this.referer = pageUrl
        }

        callback(link)
        return true
    }
}
