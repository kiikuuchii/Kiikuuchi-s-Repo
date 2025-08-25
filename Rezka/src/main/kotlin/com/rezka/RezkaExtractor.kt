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

        // достаём id, translator_id, season, episode
        val id = firstGroup(Regex("""data-id=["'](\d+)["']"""), html)
        val translator = firstGroup(Regex("""data-translator_id=["'](\d+)["']"""), html)
        val season = firstGroup(Regex("""data-season=["'](\d+)["']"""), html)
        val episode = firstGroup(Regex("""data-episode=["'](\d+)["']"""), html)

        println("RezkaExtractor: id=$id, translator=$translator, season=$season, episode=$episode")

        if (id == null || translator == null) return false

        val path = if (season != null && episode != null) "/ajax/get_cdn_series/" else "/ajax/get_cdn_files/"
        val form = if (season != null && episode != null) {
            mapOf("id" to id, "translator_id" to translator, "season" to season, "episode" to episode, "action" to "get_stream")
        } else {
            mapOf("id" to id, "translator_id" to translator, "action" to "get_movie")
        }

        val text = app.post(base + path, data = form, headers = headers).text
        val json = try { JSONObject(text) } catch (_: Throwable) { null } ?: return false

        val enc = json.optString("url")
        var decoded = decode(enc).ifBlank { enc }

        println("RezkaExtractor: decoded=$decoded")

        var yielded = false

        // m3u8
        Regex("""https?://[^\s,]+\.m3u8[^\s,]*""").findAll(decoded).map { it.value }.distinct().forEach { u ->
            M3u8Helper.generateM3u8("Rezka", u, pageUrl, headers = headers).forEach {
                callback(it); yielded = true
            }
        }

        // [1080p]URL
        Regex("""\[(.+?)\]([^\s,\[]+)""").findAll(decoded).forEach { m ->
            val qName = m.groupValues[1]
            val url = m.groupValues[2].replace("\\u0026", "&")

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

        // ts → mp4
        if (!yielded) {
            Regex("""https?://[^\s"'<>]+?\.mp4:hls:[^"'<>]+?\.ts""")
                .find(decoded)?.value?.let { seg ->
                    val mp4 = seg.substringBefore(":hls:")
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
            subtitleCallback(SubtitleFile("ru", url))
        }
    }
}