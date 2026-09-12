package io.hammerhead.karooexttemplate.extension

import androidx.compose.ui.graphics.Color
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

data class ClimbPower(
    val onClimb: Boolean,
    val watts: Double?,
)

data class Accumulator(
    val onClimb: Boolean = false,
    val lastDistanceFromBottom: Double? = null,
    val totalWattsForClimb: Double = 0.0,
    val samplesTaken: Int = 0,
)

fun KarooSystemService.climbAveragePowerFlow(): Flow<ClimbPower> {
    return combine(
        streamDataFlow(DataType.Type.POWER),
        streamDataFlow(DataType.Type.CLIMB),
        streamDataFlow(DataType.Type.AVERAGE_POWER),
    ) { power, climb, rideAvg -> Triple(power, climb, rideAvg) }
        .scan(Accumulator() to ClimbPower(onClimb = false, watts = null)) { (acc, _), (power, climb, rideAvg) ->
            // Just show the ride average watts if we're not climbing
            if (climb !is StreamState.Streaming) {
                val rideWatts = when (val r = rideAvg) {
                    is StreamState.Streaming -> r.dataPoint.singleValue
                    else -> null
                }
                acc to ClimbPower(onClimb = false, watts = rideWatts)
            } else {
                val watts = when (val p = power) {
                    is StreamState.Streaming -> p.dataPoint.singleValue
                    else -> null
                }
                val distance = climb.dataPoint.values[DataType.Field.DISTANCE_FROM_BOTTOM]
                if (startsNewClimb(acc, distance)) {
                    Accumulator(
                        onClimb = true,
                        lastDistanceFromBottom = distance ?: acc.lastDistanceFromBottom,
                        totalWattsForClimb = watts ?: 0.0,
                        samplesTaken = if (watts != null) 1 else 0,
                    ) to ClimbPower(onClimb = true, watts = watts)
                } else {
                    val totalWatts = acc.totalWattsForClimb + (watts ?: 0.0)
                    val samples = acc.samplesTaken + if (watts != null) 1 else 0
                    acc.copy(
                        onClimb = true,
                        lastDistanceFromBottom = distance ?: acc.lastDistanceFromBottom,
                        totalWattsForClimb = totalWatts,
                        samplesTaken = samples,
                    ) to ClimbPower(onClimb = true, watts = if (samples == 0) null else totalWatts / samples)
                }
            }
        }
        .map { it.second }
}

private fun startsNewClimb(acc: Accumulator, distance: Double?): Boolean {
    if (!acc.onClimb) return true
    val previous = acc.lastDistanceFromBottom ?: return false
    val current = distance ?: return false
    return current < previous
}


fun ClimbPower.toStreamState(dataTypeId: String): StreamState {
    val value = watts ?: return StreamState.NotAvailable
    return StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value)))
}

fun powerZoneIndex(watts: Double, zones: List<UserProfile.Zone>): Int {
    val i = zones.indexOfFirst { watts >= it.min && watts <= it.max }
    return when {
        i >= 0 -> i
        watts < zones.first().min -> 0
        else -> zones.lastIndex
    }
}
