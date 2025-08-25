package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

class RezkaExtractor {
    suspend fun loadAll(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // сначала пробуем найти .m3u8 или mp4 прямо на странице
        val inline = tryInline(pageUrl, callback)
        if (inline) return true

        // потом пробуем iframe
        val iframe = tryIframe(pageUrl, callback)
        if (iframe) return true

        // в конце — ajax
        return tryAjax(pageUrl, subtitleCallback, callback)
    }

    // ---------- INLINE поиск ----------
    private suspend fun tryInline(
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(pageUrl).document
        val text = doc.outerHtml()
        val regex = Regex("""https?://[^\s"']+?\.(?:m3u8|mp4)""")
        val urls = regex.findAll(text).map { it.value }.toList().distinct()
        if (urls.isEmpty()) return false

        var ok = false
        for (url in urls) {
            if (url.endsWith(".m3u8")) {
                M3u8Helper.generateM3u8(
                    source = "Rezka",
                    streamUrl = url,
                    referer = pageUrl
                ).forEach { link -> callback(link); ok = true }
            } else {
                val link = newExtractorLink(
                    source = "Rezka",
                    name = "Rezka mp4",
                    url = url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = pageUrl
                    this.quality = getQualityFromName(url)
                }
                callback(link)
                ok = true
            }
        }
        return ok
    }

    // ---------- IFRAME поиск ----------
    private suspend fun tryIframe(
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(pageUrl).document
        val iframe = doc.selectFirst("iframe")?.attr("src") ?: return false
        return tryInline(iframe, callback)
    }

    // ---------- AJAX запрос ----------
    private suspend fun tryAjax(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(pageUrl).document
        val html = doc.outerHtml()

        val id = firstGroup(Regex("""data-(?:id|movie-id)=['"](\d+)['"]"""), html)
        val translator = firstGroup(Regex("""data-(?:translator|translator_id)=['"](\d+)['"]"""), html)
        if (id.isNullOrBlank() || translator.isNullOrBlank()) return false

        val baseUrl = Regex("""https?://[^/]+""").find(pageUrl)?.value ?: pageUrl
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
        val ok = emitFromQualityList(decoded, pageUrl, callback)

        // субтитры
        val sub = json.optString("subtitle")
        if (sub.isNotBlank()) {
            subtitleCallback(SubtitleFile("ru", baseUrl + sub))
        }

        return ok
    }

    // ---------- универсальная обработка списка ----------
    private suspend fun emitFromQualityList(
        streamsText: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val rx = Regex("""\[(.+?)\]\s*([^\s,\[\]]+)""")
        val pairs = rx.findAll(streamsText).map { it.groupValues[1] to it.groupValues[2] }.toList()
        if (pairs.isEmpty()) return false

        var ok = false
        for ((qName, urlRaw) in pairs) {
            val url = urlRaw.replace("\\u0026", "&")
            val quality = getQualityFromName(qName)
            if (url.contains(".m3u8")) {
                M3u8Helper.generateM3u8(
                    source = "Rezka",
                    streamUrl = url,
                    referer = referer
                ).forEach { link -> callback(link); ok = true }
            } else {
                val link = newExtractorLink(
                    source = "Rezka",
                    name = "Rezka $qName",
                    url = url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer
                    this.quality = quality
                }
                callback(link)
                ok = true
            }
        }
        return ok
    }

    // ---------- хелперы ----------
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
