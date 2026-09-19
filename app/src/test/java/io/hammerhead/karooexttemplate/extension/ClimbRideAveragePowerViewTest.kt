package io.hammerhead.karooexttemplate.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ClimbRideAveragePowerViewTest : DescribeSpec({

    describe("labelFor") {
        it("labels the field as a climb average while climbing") {
            labelFor(onClimb = true) shouldBe "Avg W (Climb)"
        }

        it("labels the field as a ride average while not climbing") {
            labelFor(onClimb = false) shouldBe "Avg W (Ride)"
        }
    }

    describe("wattsLabel") {
        it("rounds watts to a whole number") {
            wattsLabel(265.4) shouldBe "265"
        }

        it("shows a placeholder when no power data is available") {
            wattsLabel(null) shouldBe "--"
        }

        it("shows a sample value in the field-picker preview") {
            wattsLabel(null, preview = true) shouldBe "42"
        }
    }

    describe("fittedValueSizeSp") {
        it("keeps the standard size when the field is tall enough") {
            fittedValueSizeSp(textSize = 48, fieldHeightDp = 240f) shouldBe 48
        }

        it("shrinks the value to fit a short field") {
            fittedValueSizeSp(textSize = 48, fieldHeightDp = 60f) shouldBe 29
        }

        it("never returns a size below one point") {
            fittedValueSizeSp(textSize = 48, fieldHeightDp = 0f) shouldBe 1
        }
    }
})