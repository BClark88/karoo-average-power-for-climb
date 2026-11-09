package io.hammerhead.karooexttemplate.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

class TemplateExtension : KarooExtension("average-power-for-climb", "1.0") {
    private val karooSystem by lazy { KarooSystemService(this) }

    override val types by lazy {
        listOf(
            ClimbAveragePowerDataType(karooSystem, extension),
        )
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem.connect()
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}