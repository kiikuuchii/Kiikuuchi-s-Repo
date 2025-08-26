package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import java.util.Base64

object RezkaExtractor {

    private const val REFERER = "https://rezka-ua.org/"

    /**
     * Главная точка — отдаёт список рабочих ссылок на видео (обычно HLS .m3u8) по всем найденным качествам.
     * @param pageUrl — страница фильма/сериала на rezka-ua.org
     */
    suspend fun getLinks(pageUrl: String): List<ExtractorLink> {
        val html = app.get(pageUrl, referer = REFERER).text
        val b64 = findStreamsBase64(html) ?: return emptyList()
        val decoded = decodeBase64Smart(b64) ?: return emptyList()
        return extractHlsLinks(decoded)
    }

    /** Ищем кусок вида:  "streams":"#<base64>"  внутри inline-скрипта initCDN... */
    private fun findStreamsBase64(html: String): String? {
        val rx = Regex("""["']streams["']\s*:\s*["']#([^"']+)["']""")
        return rx.find(html)?.groupValues?.getOrNull(1)
    }

    /** Base64 бывает url-safe и без паддинга — нормализуем и декодим. */
    private fun decodeBase64Smart(inputRaw: String): String? {
        var s = inputRaw
        // иногда встречаются экранированные слэши/обратные слэши
        s = s.replace("\\/", "/").replace("\\n", "\n")
        // url-safe → стандартный
        s = s.replace('-', '+').replace('_', '/')
        // паддинг
        val pad = (4 - (s.length % 4)) % 4
        s += "=".repeat(pad)
        return try {
            String(Base64.getDecoder().decode(s))
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * В декодированном тексте блоки вида:
     * [360p]https://...:manifest.m3u8 or https://...:manifest.m3u8 or https://...mp4
     * Разбираем по качествам, из каждого блока выбираем приоритетно m3u8 (:hls:manifest.m3u8 либо .m3u8).
     */
    private suspend fun extractHlsLinks(decoded: String): List<ExtractorLink> {
        val out = mutableListOf<ExtractorLink>()

        // забираем блок "качество + его варианты ссылок" (до следующего [xxxp] или конца)
        val blockRx = Regex("""\[(\d{3,4})p]\s*([^[]+)""", RegexOption.DOT_MATCHES_ALL)
        val urlRx = Regex("""https?://[^\s'",]+""", RegexOption.IGNORE_CASE)

        for (m in blockRx.findAll(decoded)) {
            val qStr = m.groupValues[1]
            val block = m.groupValues[2]

            val quality = when (qStr.toIntOrNull() ?: 0) {
                in 350..369 -> Qualities.P360.value
                in 370..489 -> Qualities.P480.value
                in 490..719 -> Qualities.P720.value
                in 720..1080 -> Qualities.P1080.value
                else -> Qualities.Unknown.value
            }

            // достаём все URL из блока
            val candidates = urlRx.findAll(block).map { it.value }.toList()
            if (candidates.isEmpty()) continue

            // приоритет: содержит ":hls:manifest.m3u8" → заканчивается на ".m3u8" → иначе mp4 как fallback
            val hls1 = candidates.firstOrNull { it.contains(":hls:manifest.m3u8") }
            val hls2 = candidates.firstOrNull { it.endsWith(".m3u8", ignoreCase = true) }
            val mp4  = candidates.firstOrNull { it.endsWith(".mp4", ignoreCase = true) }

            val chosen = hls1 ?: hls2 ?: mp4 ?: continue

            if (chosen.endsWith(".m3u8", ignoreCase = true)) {
    val items = M3u8Helper.generateM3u8(
        source = "Rezka ${qStr}p",
        streamUrl = chosen,
        referer = REFERER
    )
    items.lastOrNull()?.let { out.add(it) }
} else {
    out.add(
    newExtractorLink(
        source = "Rezka",
        name = "Rezka $qStr",
        url = chosen,
    )
  )
}
        }

        // убираем дубли по URL
        return out.distinctBy { it.url }
    }
}