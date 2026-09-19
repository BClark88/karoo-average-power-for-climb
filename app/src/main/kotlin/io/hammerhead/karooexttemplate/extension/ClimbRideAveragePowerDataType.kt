package io.hammerhead.karooexttemplate.extension

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extension data type that exposes `climb-ride-avg-power` to the Karoo system as a custom view.
 *
 * Renders a data field whose label swaps between "Avg W (Climb)" and "Avg W (Ride)"
 * depending on climb state. The underlying value comes from the POWER, CLIMB, and AVERAGE_POWER
 * system data types provided by [karooSystem].
 *
 * @param karooSystem the Karoo system service used to observe the underlying data streams
 * @param extension the extension id, passed through to the [DataTypeImpl] base class
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class ClimbRideAveragePowerDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
) : DataTypeImpl(extension, "climb-ride-avg-power") {

    private val glance = GlanceRemoteViews()

    /**
     * Starts rendering the climb/ride average-power view to the provided emitter.
     *
     * Hides Karoo's static field header, then pushes a Glance-rendered [ClimbRideAveragePowerView]
     * for each climb-power update. The collection job is cancelled via the emitter's cancellable
     * hook when the view detaches.
     *
     * @param context the context used to compose the remote views
     * @param config the view's placement config from the layout slot
     * @param emitter the emitter delivered to the Karoo system
     */
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            karooSystem.climbAveragePowerFlow().collect { climb ->
                val remoteViews = withContext(Dispatchers.Main) {
                    glance.compose(context, DpSize.Unspecified) {
                        ClimbRideAveragePowerView(
                            climb,
                            config.alignment,
                            config.textSize,
                            config.viewSize,
                            config.preview,
                        )
                    }.remoteViews
                }
                emitter.updateView(remoteViews)
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}