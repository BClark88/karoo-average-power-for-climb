package io.hammerhead.karooexttemplate.extension

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.roundToInt

/**
 * Fraction of the value font size used for the label above it.
 */
private const val LABEL_SCALE = 0.33f

/**
 * Multiplier between a font size and its rendered line height.
 */
private const val LINE_HEIGHT_FACTOR = 1.2f

/**
 * Karoo's display density, used to convert [ViewConfig.viewSize] pixels to dp.
 */
private const val KAROO_DENSITY = 2.0f

/**
 * Watts shown in the Karoo field-picker preview, which has no live power data.
 */
const val PREVIEW_WATTS = 42

/**
 * The label shown for the average-power value, matching what the value represents.
 *
 * @param onClimb whether the rider is currently on a climb
 * @return "Avg W (Climb)" while climbing, otherwise "Avg W (Ride)"
 */
fun labelFor(onClimb: Boolean): String =
    if (onClimb) "Avg W (Climb)" else "Avg W (Ride)"

/**
 * The watts value formatted for the field, or a placeholder when no data is available.
 *
 * In the Karoo field-picker preview there is no live data, so a sample value is shown instead.
 *
 * @param watts the average watts, or `null` when no power data is available
 * @param preview whether the field is rendering in the Karoo field-picker preview
 * @return the watts rounded to a whole number, or "--" when `watts` is `null`
 */
fun wattsLabel(watts: Double?, preview: Boolean = false): String =
    if (preview) PREVIEW_WATTS.toString() else watts?.let { it.roundToInt().toString() } ?: "--"

/**
 * The largest value font size (sp) that fits the field height alongside the label.
 *
 * Karoo's standard numeric view sizes its value to the field; a Glance custom view must do
 * the same explicitly, otherwise the value overflows and is clipped. [textSize] is the
 * standard size for the field's grid, so the result never exceeds it.
 *
 * @param textSize the field's configured value text size in sp
 * @param fieldHeightDp the field's height in dp
 * @return the value font size in sp, never larger than [textSize]
 */
internal fun fittedValueSizeSp(textSize: Int, fieldHeightDp: Float): Int {
    val labelHeightDp = textSize * LABEL_SCALE * LINE_HEIGHT_FACTOR
    val fitted = ((fieldHeightDp - labelHeightDp) / LINE_HEIGHT_FACTOR).toInt()
    return fitted.coerceIn(1, textSize)
}

/**
 * Renders the climb/ride average-power field with a label that changes with [power.onClimb].
 *
 * Shows "Avg W (Climb)" and the climb average while climbing, otherwise
 * "Avg W (Ride)" and the ride average. The label sits above the value, top-aligned, at
 * about one third of the value's font size.
 *
 * @param power the current climb-power state to render
 * @param alignment the field's configured alignment within its layout slot
 * @param textSize the field's configured value text size in sp
 * @param viewSize the field's size in pixels, from [ViewConfig.viewSize]
 * @param preview whether the field is rendering in the Karoo field-picker preview
 */
@Composable
fun ClimbRideAveragePowerView(
    power: ClimbPower,
    alignment: ViewConfig.Alignment,
    textSize: Int,
    viewSize: Pair<Int, Int>,
    preview: Boolean,
) {
    val horizontal = when (alignment) {
        ViewConfig.Alignment.LEFT -> Alignment.Horizontal.Start
        ViewConfig.Alignment.CENTER -> Alignment.Horizontal.CenterHorizontally
        ViewConfig.Alignment.RIGHT -> Alignment.Horizontal.End
    }
    val textAlign = when (alignment) {
        ViewConfig.Alignment.LEFT -> TextAlign.Start
        ViewConfig.Alignment.CENTER -> TextAlign.Center
        ViewConfig.Alignment.RIGHT -> TextAlign.End
    }
    val valueSp = fittedValueSizeSp(textSize, viewSize.second / KAROO_DENSITY)
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = horizontal,
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            Text(
                text = labelFor(power.onClimb),
                modifier = GlanceModifier.fillMaxWidth(),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color.Black),
                    fontSize = (textSize * 0.33f).sp,
                    textAlign = textAlign,
                ),
            )
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(bottom = 2.dp),
                contentAlignment = when (alignment) {
                    ViewConfig.Alignment.LEFT -> Alignment.BottomStart
                    ViewConfig.Alignment.CENTER -> Alignment.BottomCenter
                    ViewConfig.Alignment.RIGHT -> Alignment.BottomEnd
                },
            ) {
                Text(
                    text = wattsLabel(power.watts, preview),
                    modifier = GlanceModifier.fillMaxWidth(),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color.Black),
                        fontSize = valueSp.sp,
                        fontFamily = FontFamily("sans-serif-light"),
                        fontWeight = FontWeight.Normal,
                        textAlign = textAlign,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 120)
@Composable
private fun ClimbRideAveragePowerPreview() {
    ClimbRideAveragePowerView(
        power = ClimbPower(onClimb = true, watts = 265.0),
        alignment = ViewConfig.Alignment.CENTER,
        textSize = 48,
        viewSize = 480 to 240,
        preview = false,
    )
}
