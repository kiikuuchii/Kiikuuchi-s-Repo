package com.rezka

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.app

class RezkaExtractor {
    suspend fun getM3u8Links(
        url: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Загружаем HTML страницы
        val doc = app.get(url).document

        // Ищем ссылку на manifest.m3u8
        val scriptText = doc.select("script").joinToString("\n") { it.data() }
        val regex = Regex("""https[^"']+manifest\.m3u8""")
        val m3u8Url = regex.find(scriptText)?.value

        if (m3u8Url.isNullOrBlank()) return false

        println("✅ Нашли m3u8: $m3u8Url")

        // Используем правильные параметры для SDK
        M3u8Helper.generateM3u8(
            source = "Rezka",
            streamUrl = m3u8Url,
            referer = url
        ).forEach(callback)

        return true
    }
}
