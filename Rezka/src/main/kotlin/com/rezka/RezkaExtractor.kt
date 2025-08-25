package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import org.json.JSONObject

class RezkaExtractor {
    suspend fun loadAll(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // сначала пробуем найти .m3u8 прямо на странице
        val quick = loadFromInlineM3u8(pageUrl, callback)
        if (quick) return true

        // если не нашли — идём через ajax
        return loadFromAjax(pageUrl, subtitleCallback, callback)
    }

    // -------- поиск m3u8 в <script> --------
    private suspend fun loadFromInlineM3u8(
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(pageUrl).document
        val scriptText = doc.select("script").joinToString("\n") { it.data() ?: "" }

        val regex = Regex("""https?://[^\s"']+?\.m3u8""")
        val urls = regex.findAll(scriptText).map { it.value }.toList().distinct()
        if (urls.isEmpty()) return false

        for (url in urls) {
            M3u8Helper.generateM3u8(
                source = "Rezka",
                streamUrl = url,
                referer = pageUrl
            ).forEach { link -> callback(link) }
        }
        return true
    }

    // -------- ajax-запрос --------
    private suspend fun loadFromAjax(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val baseUrl = Regex("""https?://[^/]+""").find(pageUrl)?.value ?: pageUrl
        val doc = app.get(pageUrl).document
        val text = doc.outerHtml()

        val id = firstGroup(Regex("""data-(?:id|movie-id)=['"](\d+)['"]"""), text)
        val translator = firstGroup(Regex("""data-(?:translator|translator_id)=['"](\d+)['"]"""), text)

        if (id.isNullOrBlank() || translator.isNullOrBlank()) return false

        val ajaxUrl = "$baseUrl/ajax/get_cdn_series/"
        val form = mapOf(
            "id" to id,
            "translator_id" to translator,
            "action" to "get_movie"
        )
        val headers = mapOf(
            "Referer" to pageUrl,
            "Origin" to baseUrl,
            "X-Requested-With" to "XMLHttpRequest"
        )

        val response = app.post(ajaxUrl, data = form, headers = headers).text
        val json = JSONObject(response)

        val encoded = json.optString("url")
        if (encoded.isBlank()) return false

        val decoded = decodeRezkaUrl(encoded)
        val regex = Regex("""\[(.+?)\]([^\s,\[]+)""")
        val matches = regex.findAll(decoded).map { it.groupValues[1] to it.groupValues[2] }.toList()

        var ok = false
        for ((qualityName, link) in matches) {
            val url = link.replace("\\u0026", "&")
            val quality = getQualityFromName(qualityName)
            newExtractorLink(
                source = "Rezka",
                name = "Rezka $qualityName",
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = pageUrl
                this.quality = quality
            }.also(callback)
            ok = true
        }

        // субтитры
        val sub = json.optString("subtitle")
        if (sub.isNotBlank()) {
            subtitleCallback(SubtitleFile("ru", baseUrl + sub))
        }

        return ok
    }

    // -------- хелперы --------
    private fun firstGroup(regex: Regex, text: String): String? =
        regex.find(text)?.groupValues?.getOrNull(1)

    private fun decodeRezkaUrl(input: String): String {
        val clean = input.trim().trimStart('#')
        return try {
            String(java.util.Base64.getDecoder().decode(clean))
        } catch (_: Throwable) {
            ""
        }
    }
}