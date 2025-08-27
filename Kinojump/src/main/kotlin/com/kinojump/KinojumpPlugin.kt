package com.kinojump


import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class KinojumpPlugin : Plugin() {
override fun load(context: Context) {
registerMainAPI(Kinojump())
}
}