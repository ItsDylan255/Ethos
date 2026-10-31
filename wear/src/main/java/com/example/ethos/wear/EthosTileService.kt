package com.example.ethos.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

private const val RESOURCES_VERSION = "1"
private const val ACCENT = 0xFF8C95F0.toInt()
private const val TRACK = 0xFF2A2C3A.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val GREY = 0xFFB0B3C0.toInt()

/** Swipe tile: today's progress and what is still open. Tap opens the watch app. */
class EthosTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val today = runCatching { PhoneLink(this@EthosTileService).load() }.getOrNull()?.takeIf { it.isCurrent }
            val layout = layout(this@EthosTileService, requestParams.deviceConfiguration, today)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                // New data also triggers an update (PhoneListenerService); this covers midnight.
                .setFreshnessIntervalMillis(30 * 60 * 1000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        scope.future { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

private fun layout(context: Context, device: DeviceParameters, today: WatchToday?): LayoutElementBuilders.LayoutElement {
    val total = today?.items?.size ?: 0
    val done = today?.done ?: 0
    val open = today?.items?.filterNot { it.success }.orEmpty()
    val main = if (today == null) "–" else "$done/$total"
    val secondary = when {
        today == null -> "Öffne Ethos auf dem Handy"
        total == 0 -> "Heute nichts geplant"
        open.isEmpty() -> "Alles geschafft"
        else -> "Offen: " + open.joinToString(", ") { it.title }
    }

    val content = EdgeContentLayout.Builder(device)
        .setEdgeContent(
            CircularProgressIndicator.Builder()
                .setProgress(if (total == 0) 0f else done.toFloat() / total)
                .setCircularProgressIndicatorColors(ProgressIndicatorColors(ACCENT, TRACK))
                .build(),
        )
        .setPrimaryLabelTextContent(
            Text.Builder(context, "Heute").setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(ACCENT)).build(),
        )
        .setContent(
            Text.Builder(context, main).setTypography(Typography.TYPOGRAPHY_DISPLAY2).setColor(argb(WHITE)).build(),
        )
        .setSecondaryLabelTextContent(
            Text.Builder(context, secondary)
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .setColor(argb(GREY))
                .setMaxLines(2)
                .build(),
        )
        .build()

    val openApp = ModifiersBuilders.Clickable.Builder()
        .setId("open")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build(),
                )
                .build(),
        )
        .build()

    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
        .addContent(content)
        .build()
}

/** The phone sent new data: redraw the tile. */
class PhoneListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(EthosTileService::class.java)
    }
}
