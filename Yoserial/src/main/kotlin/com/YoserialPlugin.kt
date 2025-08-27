package com.yoserial


import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class YoserialPlugin : Plugin() {
override fun load(context: Context) {
registerMainAPI(Yoserial())
}
}