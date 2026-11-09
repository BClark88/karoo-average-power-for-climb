package io.hammerhead.karooexttemplate.extension

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ClimbAveragePowerTest : DescribeSpec({

    val rest = Accumulator()

    val midClimb = Accumulator(
        onClimb = true,
        lastDistanceFromBottom = 2000.0,
        totalWattsForClimb = 500.0,
        samplesTaken = 2,
    )

    fun power(watts: Double): StreamState =
        StreamState.Streaming(DataPoint("power-id", mapOf(DataType.Field.SINGLE to watts)))

    fun climbAt(distanceFromBottom: Double): StreamState =
        StreamState.Streaming(DataPoint("climb-id", mapOf(DataType.Field.DISTANCE_FROM_BOTTOM to distanceFromBottom)))

    fun noSignal(): StreamState = StreamState.NotAvailable

    describe("accumulate") {
        context("when not on a climb") {
            it("forwards the ride average power and leaves the accumulator resting") {
                val (acc, climb) = accumulate(rest, noSignal(), noSignal(), power(220.0))
                climb.onClimb shouldBe false
                climb.watts shouldBe 220.0
                acc shouldBe rest
            }

            it("reports no watts when the ride average is not streaming") {
                val (_, climb) = accumulate(rest, noSignal(), noSignal(), noSignal())
                climb.onClimb shouldBe false
                climb.watts shouldBe null
            }
        }

        context("when a climb begins") {
            it("opens the accumulator with the first power sample") {
                val (acc, climb) = accumulate(rest, power(250.0), climbAt(0.0), noSignal())
                climb.onClimb shouldBe true
                climb.watts shouldBe 250.0
                acc shouldBe Accumulator(
                    onClimb = true,
                    lastDistanceFromBottom = 0.0,
                    totalWattsForClimb = 250.0,
                    samplesTaken = 1,
                )
            }

            it("opens the accumulator even when the power meter has no signal yet") {
                val (acc, climb) = accumulate(rest, noSignal(), climbAt(0.0), noSignal())
                climb.onClimb shouldBe true
                climb.watts shouldBe null
                acc.samplesTaken shouldBe 0
                acc.totalWattsForClimb shouldBe 0.0
            }
        }

        context("mid-climb") {
            it("averages every power sample since the climb began") {
                val (acc, climb) = accumulate(midClimb, power(400.0), climbAt(4000.0), power(190.0))
                climb.onClimb shouldBe true
                climb.watts shouldBe 300.0
                acc.samplesTaken shouldBe 3
                acc.totalWattsForClimb shouldBe 900.0
            }

            it("ignores samples during a power signal gap") {
                val (acc, climb) = accumulate(midClimb, noSignal(), climbAt(4000.0), power(190.0))
                climb.onClimb shouldBe true
                climb.watts shouldBe 250.0
                acc.samplesTaken shouldBe 2
                acc.totalWattsForClimb shouldBe 500.0
            }

            it("keeps climbing while the distance from the bottom increases") {
                val (acc, climb) = accumulate(midClimb, power(400.0), climbAt(4000.0), noSignal())
                climb.onClimb shouldBe true
                acc.lastDistanceFromBottom shouldBe 4000.0
            }
        }

        context("when DISTANCE_FROM_BOTTOM drops") {
            it("starts a fresh accumulator for the new climb") {
                val (acc, climb) = accumulate(midClimb, power(400.0), climbAt(40.0), noSignal())
                climb.onClimb shouldBe true
                climb.watts shouldBe 400.0
                acc shouldBe Accumulator(
                    onClimb = true,
                    lastDistanceFromBottom = 40.0,
                    totalWattsForClimb = 400.0,
                    samplesTaken = 1,
                )
            }
        }
    }

    describe("toStreamState") {
        val dataTypeId = "average-power-for-climb:climb-average-power"

        it("emits the running climb average while climbing") {
            val state = ClimbPower(onClimb = true, watts = 300.0).toStreamState(dataTypeId)
            state shouldBe StreamState.Streaming(
                DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to 300.0)),
            )
        }

        it("reports no value when not climbing") {
            val state = ClimbPower(onClimb = false, watts = 220.0).toStreamState(dataTypeId)
            state shouldBe StreamState.NotAvailable
        }

        it("reports no value while climbing without watts") {
            val state = ClimbPower(onClimb = true, watts = null).toStreamState(dataTypeId)
            state shouldBe StreamState.NotAvailable
        }
    }
})
