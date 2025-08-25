package com.rezka

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import java.util.Base64

class RezkaExtractor {

    suspend fun loadLinks(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val base = Regex("""https?://[^/]+""").find(pageUrl)?.value ?: "https://rezka-ua.org"
        val headers = mapOf(
            "Referer" to pageUrl,
            "Origin" to base,
            "X-Requested-With" to "XMLHttpRequest"
        )

        val doc = app.get(pageUrl, headers = headers).document
        val html = doc.outerHtml()

        var yielded = false

        // Debug лог
        println("=== RezkaExtractor ===")
        println("Page URL: $pageUrl")
        println("Base: $base")

        // 1) Поиск прямых .mp4 через ts:hls
        Regex("""https?://[^\s"'<>]+?\.mp4:hls:[^"'<>]+?\.ts""")
            .find(html)?.value?.let { seg ->
                val mp4 = seg.substringBefore(":hls:")
                println("Found MP4 from ts: $mp4")
                val link = newExtractorLink(
                    source = "Rezka",
                    name = "Rezka [MP4]",
                    url = mp4
                ) {
                    this.referer = pageUrl
                    this.quality = 1080
                }
                callback(link)
                return true
            }

        // 2) Прямые .m3u8 в html
        Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
            .findAll(html).map { it.value }.distinct().forEach { m3u8 ->
                println("Found m3u8 in HTML: $m3u8")
                M3u8Helper.generateM3u8("Rezka", m3u8, pageUrl, headers = headers).forEach {
                    callback(it); yielded = true
                }
            }
        if (yielded) return true

        // 3) JSON "file":"..."
        Regex(""""file"\s*:\s*"([^"]+)"""")
            .find(html)?.groupValues?.getOrNull(1)?.let { fileUrl ->
                println("Found file JSON: $fileUrl")
                if (fileUrl.contains(".m3u8")) {
                    M3u8Helper.generateM3u8("Rezka", fileUrl, pageUrl, headers = headers).forEach {
                        callback(it); yielded = true
                    }
                    if (yielded) return true
                } else if (fileUrl.contains(".mp4")) {
                    val link = newExtractorLink(
                        source = "Rezka",
                        name = "Rezka [MP4]",
                        url = fileUrl
                    ) {
                        this.referer = pageUrl
                        this.quality = 1080
                    }
                    callback(link)
                    return true
                }
            }

        // 4) AJAX запрос
        val id = firstGroup(Regex("""data-(?:id|movie-id)=['"](\d+)['"]"""), html)
            ?: firstGroup(Regex("""\bid\s*:\s*(\d{3,})"""), html)
        val translator = firstGroup(Regex("""data-(?:translator|translator_id)=['"](\d+)['"]"""), html)
            ?: firstGroup(Regex("""\btranslator_id\s*:\s*(\d{1,})"""), html)
        val season = firstGroup(Regex("""data-season=['"](\d+)['"]"""), html)
        val episode = firstGroup(Regex("""data-episode=['"](\d+)['"]"""), html)

        if (id == null || translator == null) {
            println("ID or translator not found!")
            return false
        }

        val path = if (season != null && episode != null) "/ajax/get_cdn_series/" else "/ajax/get_cdn_files/"
        val form = if (season != null && episode != null)
            mapOf("id" to id, "translator_id" to translator, "season" to season, "episode" to episode, "action" to "get_stream")
        else
            mapOf("id" to id, "translator_id" to translator, "action" to "get_movie")

        println("AJAX Request: $base$path")
        println("Form: $form")

        val text = app.post(base + path, data = form, headers = headers).text
        println("AJAX Response: $text")

        val json = try { JSONObject(text) } catch (_: Throwable) { null } ?: return false
        val enc = json.optString("url")
        var decoded = decode(enc).ifBlank { enc }

        println("Decoded: $decoded")

        // a) ищем m3u8
        Regex("""https?://[^\s,]+\.m3u8[^\s,]*""").findAll(decoded).map { it.value }.distinct().forEach { u ->
            println("Found m3u8 in decoded: $u")
            M3u8Helper.generateM3u8("Rezka", u, pageUrl, headers = headers).forEach {
                callback(it); yielded = true
            }
        }
        if (yielded) {
            pushSubs(base, json, subtitleCallback)
            return true
        }

        // b) [1080p]URL,[720p]URL...
        Regex("""\[(.+?)\]([^\s,\[]+)""").findAll(decoded).forEach { m ->
            val qName = m.groupValues[1]
            val url = m.groupValues[2].replace("\\u0026", "&")
            println("Found quality URL: [$qName] $url")

            if (url.contains(".m3u8")) {
                M3u8Helper.generateM3u8("Rezka", url, pageUrl, headers = headers).forEach {
                    callback(it); yielded = true
                }
            } else {
                val link = newExtractorLink(
                    source = "Rezka",
                    name = "Rezka [$qName]",
                    url = url
                ) {
                    this.referer = pageUrl
                    this.quality = getQualityFromName(qName)
                }
                callback(link)
                yielded = true
            }
        }

        // c) ts → mp4 в decoded
        if (!yielded) {
            Regex("""https?://[^\s"'<>]+?\.mp4:hls:[^"'<>]+?\.ts""")
                .find(decoded)?.value?.let { seg ->
                    val mp4 = seg.substringBefore(":hls:")
                    println("Found MP4 from ts (decoded): $mp4")
                    val link = newExtractorLink(
                        source = "Rezka",
                        name = "Rezka [MP4]",
                        url = mp4
                    ) {
                        this.referer = pageUrl
                        this.quality = 1080
                    }
                    callback(link)
                    yielded = true
                }
        }

        pushSubs(base, json, subtitleCallback)
        return yielded
    }

    private fun firstGroup(regex: Regex, text: String): String? {
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun decode(s: String?): String {
        return try {
            if (s.isNullOrBlank()) return ""
            val clean = s.trim().trimStart('#')
            String(Base64.getDecoder().decode(clean))
        } catch (_: Throwable) {
            ""
        }
    }

    private fun pushSubs(base: String, json: JSONObject, subtitleCallback: (SubtitleFile) -> Unit) {
        val sub = json.optString("subtitle")
        if (!sub.isNullOrBlank()) {
            val url = if (sub.startsWith("http")) sub else base + sub
            println("Found subtitle: $url")
            subtitleCallback(SubtitleFile("ru", url))
        }
    }
}
