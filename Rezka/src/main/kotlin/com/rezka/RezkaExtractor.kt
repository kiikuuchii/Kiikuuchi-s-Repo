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
        debug("Открываем страницу: $pageUrl")
        return loadFromAjax(pageUrl, subtitleCallback, callback)
    }

    private suspend fun loadFromAjax(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val baseUrl = Regex("""https?://[^/]+""").find(pageUrl)?.value ?: pageUrl
        val doc = app.get(pageUrl).document
        val text = doc.outerHtml()

        // Парсим id и переводчика
        val id = firstGroup(Regex("""data-(?:id|movie-id)=['"](\d+)['"]"""), text)
        val translator = firstGroup(Regex("""data-(?:translator|translator_id)=['"](\d+)['"]"""), text)
        debug("Нашли id=$id, translator=$translator")

        if (id.isNullOrBlank() || translator.isNullOrBlank()) {
            debug("❌ Не удалось найти id или translator_id")
            return false
        }

        val ajaxUrls = listOf(
            "$baseUrl/ajax/get_cdn_movie/",
            "$baseUrl/ajax/get_cdn_series/"
        )

        var ok = false
        for (ajaxUrl in ajaxUrls) {
            try {
                debug("Пробуем ajax: $ajaxUrl")

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
                debug("AJAX ответ: ${response.take(200)}")

                val json = JSONObject(response)
                val encoded = json.optString("url")
                if (encoded.isBlank()) continue

                val decoded = decodeRezkaUrl(encoded)
                debug("Декодированная строка: ${decoded.take(200)}")

                val regex = Regex("""\[(.+?)\]([^\s,\[]+)""")
                val matches = regex.findAll(decoded).map { it.groupValues[1] to it.groupValues[2] }.toList()

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

                    debug("✅ Нашли поток: $qualityName → $url")
                    ok = true
                }

                // субтитры
                val sub = json.optString("subtitle")
                if (sub.isNotBlank()) {
                    subtitleCallback(SubtitleFile("ru", baseUrl + sub))
                    debug("Добавили субтитры: $sub")
                }

                if (ok) break

            } catch (e: Exception) {
                debug("Ошибка ajax: ${e.message}")
            }
        }

        if (!ok) debug("❌ Не нашли ссылки ни через movie, ни через series")
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

    private fun debug(msg: String) {
        println("[RezkaExtractor] $msg")
    }
}