package com.rezka

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.app

class RezkaExtractor {
    suspend fun loadM3u8Links(
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Загружаем страницу
        val doc = app.get(pageUrl).document

        // Собираем весь текст скриптов
        val scriptText = doc.select("script").joinToString("\n") { it.data() ?: "" }

        // Ищем URL, заканчивающийся на manifest.m3u8
        val regex = Regex("""https?://[^\s"']+manifest\.m3u8""")
        val match = regex.find(scriptText)
        val m3u8Url = match?.value ?: return false

        println("Found manifest: $m3u8Url")

        // Используем встроенный разбивщик плейлиста
        M3u8Helper.generateM3u8(
            source = "Rezka",
            streamUrl = m3u8Url,
            referer = pageUrl
        ).forEach { link ->
            callback(link)
        }

        return true
    }
}
