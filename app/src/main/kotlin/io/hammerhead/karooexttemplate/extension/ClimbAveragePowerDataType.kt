package io.hammerhead.karooexttemplate.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Extension data type that exposes `climb-avg-power` to the Karoo system.
 *
 * Streams climb-average power derived from the POWER, CLIMB, and AVERAGE_POWER system data
 * types provided by [karooSystem].
 *
 * @param karooSystem the Karoo system service used to observe the underlying data streams
 * @param extension the extension id, passed through to the [DataTypeImpl] base class
 */
class ClimbAveragePowerDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
) : DataTypeImpl(extension, "climb-avg-power") {

    /**
     * Starts emitting climb-average power to the provided emitter.
     *
     * The collection job is cancelled via the emitter's cancellable hook when streaming stops.
     *
     * @param emitter the emitter delivered to the Karoo system
     */
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.climbAveragePowerFlow().collect { climb ->
                emitter.onNext(climb.toStreamState(dataTypeId))
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}
