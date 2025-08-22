package com.rezka


import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class RezkaPlugin : Plugin() {
override fun load(context: Context) {
registerMainAPI(Rezka())
}
}