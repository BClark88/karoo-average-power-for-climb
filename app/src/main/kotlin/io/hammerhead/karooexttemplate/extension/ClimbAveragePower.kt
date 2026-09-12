package io.hammerhead.karooexttemplate.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * The current state of climb-average power.
 *
 * @property onClimb whether the rider is currently on a climb
 * @property watts average watts for the current climb, or the ride-average watts while not
 *   climbing; `null` when no power data is available
 */
data class ClimbPower(
    val onClimb: Boolean,
    val watts: Double?,
)

/**
 * Running state used to accumulate climb-average power across stream events.
 *
 * Not part of the extension's public interface; it is public only because [accumulate]
 * takes it as a parameter.
 *
 * @property onClimb whether the most recent event processed was on a climb
 * @property lastDistanceFromBottom distance-from-bottom (m) of the last processed climb sample
 * @property totalWattsForClimb sum of watt values collected for the current climb
 * @property samplesTaken number of power samples collected for the current climb
 */
data class Accumulator(
    val onClimb: Boolean = false,
    val lastDistanceFromBottom: Double? = null,
    val totalWattsForClimb: Double = 0.0,
    val samplesTaken: Int = 0,
)

/**
 * Emits [ClimbPower] for each combined POWER, CLIMB, and AVERAGE_POWER stream event.
 *
 * While climbing, watts is the running average for the current climb, resetting whenever a
 * new climb is detected (distance-from-bottom resets). While not climbing, watts is the
 * ride-average power.
 *
 * @receiver the Karoo system service providing the underlying data streams
 * @return a cold flow of climb-average power updates
 */
fun KarooSystemService.climbAveragePowerFlow(): Flow<ClimbPower> {
    return combine(
        streamDataFlow(DataType.Type.POWER),
        streamDataFlow(DataType.Type.CLIMB),
        streamDataFlow(DataType.Type.AVERAGE_POWER),
    ) { power, climb, rideAvg -> Triple(power, climb, rideAvg) }
        .scan(Accumulator() to ClimbPower(onClimb = false, watts = null)) { (acc, _), (power, climb, rideAvg) ->
            accumulate(acc, power, climb, rideAvg)
        }
        .map { it.second }
}

internal fun accumulate(
    acc: Accumulator,
    power: StreamState,
    climb: StreamState,
    rideAvg: StreamState,
): Pair<Accumulator, ClimbPower> {
    if (climb !is StreamState.Streaming) {
        // Just show the ride average watts if we're not climbing
        val rideWatts = when (val r = rideAvg) {
            is StreamState.Streaming -> r.dataPoint.singleValue
            else -> null
        }
        return acc to ClimbPower(onClimb = false, watts = rideWatts)
    }
    val watts = when (val p = power) {
        is StreamState.Streaming -> p.dataPoint.singleValue
        else -> null
    }
    val distance = climb.dataPoint.values[DataType.Field.DISTANCE_FROM_BOTTOM]
    if (startsNewClimb(acc, distance)) {
        return Accumulator(
            onClimb = true,
            lastDistanceFromBottom = distance ?: acc.lastDistanceFromBottom,
            totalWattsForClimb = watts ?: 0.0,
            samplesTaken = if (watts != null) 1 else 0,
        ) to ClimbPower(onClimb = true, watts = watts)
    }
    val totalWatts = acc.totalWattsForClimb + (watts ?: 0.0)
    val samples = acc.samplesTaken + if (watts != null) 1 else 0
    return acc.copy(
        onClimb = true,
        lastDistanceFromBottom = distance ?: acc.lastDistanceFromBottom,
        totalWattsForClimb = totalWatts,
        samplesTaken = samples,
    ) to ClimbPower(onClimb = true, watts = if (samples == 0) null else totalWatts / samples)
}

private fun startsNewClimb(acc: Accumulator, distance: Double?): Boolean {
    if (!acc.onClimb) return true
    val previous = acc.lastDistanceFromBottom ?: return false
    val current = distance ?: return false
    return current < previous
}

/**
 * Converts this climb-power value into a [StreamState] ready for the emitter.
 *
 * @param dataTypeId the data type id to attach to the streaming data point
 * @return [StreamState.Streaming] when watts are available, otherwise [StreamState.NotAvailable]
 */
fun ClimbPower.toStreamState(dataTypeId: String): StreamState {
    val value = watts ?: return StreamState.NotAvailable
    return StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value)))
}
