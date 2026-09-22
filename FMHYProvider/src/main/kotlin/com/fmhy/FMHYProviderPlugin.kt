package com.fmhy

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FMHYProviderPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FMHYProvider())
    }
}
