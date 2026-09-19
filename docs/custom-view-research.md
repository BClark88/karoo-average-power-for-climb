# Research: dynamic "Avg Power (Climb) / Avg Power (Ride)" label

Status: research only, not yet implemented.
Goal for later: a single data field whose label says **"Avg Power (Climb)"** while climbing and
**"Avg Power (Ride)"** otherwise.

## Why a standard data field cannot relabel itself

Karoo derives every data type (including its `displayName`) statically from
`res/xml/extension_info.xml`. On the ride screen, a plain data field renders Karoo's own
typography using that fixed `displayName` as the header, and the numeric value streamed by
the extension below it.

SDK facts (karoo-ext 1.1.9, confirmed by inspecting `karoo-ext-1.1.9.aar` and
https://hammerheadnav.github.io/karoo-ext):

- `DataType(extension, typeId, displayName, description, graphical, icon)` — docs: "Derived
  from XML meta-data in ExtensionInfo." No units/label field, and no mutation.
- `KarooExtension.types: List<DataTypeImpl>` — docs: "a list of pre-defined **static** data
  type implementations."
- `DataTypeImpl` exposes only `startStream(Emitter<StreamState>)` and
  `startView(Context, ViewConfig, ViewEmitter)`.
- The AIDL surface (`IKarooExtension`) has no call to update or rename a data type at runtime.
  `startStream` may only emit numeric `StreamState` values (`DataPoint.values` are all
  `Double`).

So the label on a standard field is fixed for the life of the field. The value already
switches correctly (ride-average watts off-climb, climb-average watts on-climb) — only the
label is stuck.

## The custom-view route (the one way to get a self-relabeling field)

A `DataTypeImpl` may implement `startView`, building a `RemoteViews` tree that the Karoo ride
app / pages app renders inside the field. Because the extension author draws the whole thing,
we can render our own label text driven by climb state. This is the documented "custom view
for a data type" feature; the upstream sample implements it for `custom-speed`
(`karoo-ext-sample/app/src/main/kotlin/io/hammerhead/sampleext/extension/CustomSpeedDataType.kt`).

### API surface used for the custom view

```kotlin
class ClimbAveragePowerDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
) : DataTypeImpl(extension, "climb-avg-power") {

    override fun startStream(emitter: Emitter<StreamState>) { /* existing numeric streaming */ }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // config: gridSize, viewSize, textSize, alignment (LEFT/CENTER/RIGHT),
        //         boundariesEnabled, preview
        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.climbAveragePowerFlow().collect { climb ->
                val views = glance.compose(context, DpSize.Unspecified) {
                    ClimbAveragePowerWidget(climb, config.alignment, config.textSize)
                }
                emitter.updateView(views.remoteViews)
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}
```

Key pieces:

- **`GlanceRemoteViews()`** (`androidx.glance.appwidget.GlanceRemoteViews`) +
  `compose(context, DpSize.Unspecified) { ... }` produces the `RemoteViews`. The Glance
  composable draws label + value from a `ClimbPower` (`onClimb: Boolean, watts: Double?`).
- **`ViewEmitter.updateView(RemoteViews)`** pushes each render.
- **Hiding Karoo's static header**: emit
  `UpdateGraphicConfig(showHeader = false)` so Karoo does not draw the fixed data-type
  `displayName` over/above our content. `formatDataTypeId` (a `DataType.Type`, e.g.
  `DataType.Type.AVERAGE_POWER`) lets Karoo format/choose style for the numeric value if we
  don't render it ourselves.
- **ViewEvent subclasses**: `UpdateGraphicConfig(showHeader: Boolean?, formatDataTypeId: String?)`
  and `UpdateNumericConfig(formatDataTypeId: String?)`.
- **`config.alignment`** (`ViewConfig.Alignment.LEFT/CENTER/RIGHT`), **`config.textSize`**
  and `gridSize`/`viewSize` should be honored so the field matches the layout slot.

### Sample wiring already in this repo

- `gradle/libs.versions.toml` already declares `androidx-glance-appwidget` (glance 1.1.1) and
  `androidx-glance-preview` — but `app/build.gradle.kts` does **not** add them to
  `dependencies` yet. That is step 1.
- `ClimbAveragePower.kt` exposes `climbAveragePowerFlow(): Flow<ClimbPower>` — the custom view
  consumes exactly this, so no new accumulation logic is needed.
- `extension_info.xml` `displayName` stays a static fallback string (shown in the layout
  picker and as the field header until a view is drawn). No XML change strictly required for
  the view feature.

## Implementation plan (deferred, for later)

1. `app/build.gradle.kts`: add
   `implementation(libs.androidx.glance.appwidget)` and
   `implementation(libs.androidx.glance.preview)` (plus
   `androidx-glance-appwidget-preview` if we want Glance previews).
2. New file `ClimbAveragePowerWidget.kt`: Glance composable taking
   `(power: ClimbPower, alignment: ViewConfig.Alignment, textSize: Int)` that renders:
   - label `"Avg Power (Climb)"` when `power.onClimb`, else `"Avg Power (Ride)"`
   - value: `power.watts` rounded to integer (show `"--"` when `null`)
   - include a Glance `@Preview` for layout tuning.
3. `ClimbAveragePowerWidget.kt` (or a small pure helper
   `labelFor(onClimb: Boolean): String`) keeps labelling logic unit-testable — mirrors the
   existing `ClimbPower`/`Accumulator` test style in kotest.
4. `ClimbAveragePowerDataType.kt`: implement `startView` (see skeleton above). Emit
   `UpdateGraphicConfig(showHeader = false)` once, then render `climbAveragePowerFlow()`
   updates via `emitter.updateView(...)`. Keep existing `startStream` for the standard field.
5. Wire nothing new in `TemplateExtension.kt` — the data type already registers the type.

## Caveats / open questions to verify on device

- **How the user picks the custom view** in the ride layout editor (custom view vs. default
  numeric field for the same data type) — confirm UX on hardware; the sample ships the custom
  view for `custom-speed` and shows layout additions in a `startView`.
- Glance text won't automatically match Karoo's native field typography; expect to tune
  sizes/paddings off `config.textSize`/`gridSize`.
- Whether `graphical="true"` matters for `startView` data types (upstream sample marks the
  custom-view type graphical) — verify; if required, flip `climb-avg-power` to graphical.
- Field preview rendering in the layout editor (`config.preview`) — decide whether the view
  renders placeholder content in preview.
- If multiple `ViewEmitter`/`startView` calls come as the screen changes pages, re-render and
  cancel stale jobs correctly (match sample's `emitter.setCancellable` pattern).

## References

- karoo-ext docs: https://hammerheadnav.github.io/karoo-ext/
  - `DataType`: https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/-data-type/index.html
  - `DataTypeImpl.startView`: https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.extension/-data-type-impl/start-view.html
  - `ViewConfig`: models `ViewConfig` (alignment, textSize, grid/viewSize)
  - `ViewEmitter`/`ViewEvent`: `UpdateGraphicConfig`, `UpdateNumericConfig`
- Upstream sample custom view:
  `karoo-ext` repo, `app/src/main/kotlin/io/hammerhead/sampleext/extension/CustomSpeedDataType.kt`
  and `CustomSpeed.kt`.