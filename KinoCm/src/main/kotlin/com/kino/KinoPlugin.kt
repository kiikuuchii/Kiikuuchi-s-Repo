package com.kino


import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class KinoPlugin : Plugin() {
override fun load(context: Context) {
registerMainAPI(Kino())
}
}