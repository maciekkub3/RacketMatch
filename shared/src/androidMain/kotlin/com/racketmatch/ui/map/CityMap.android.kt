package com.racketmatch.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.Sport
import com.racketmatch.ui.theme.ProCircuit
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val CartoDarkTiles = object : OnlineTileSourceBase(
    "CartoDark", 0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "${baseUrl}${MapTileIndex.getZoom(pMapTileIndex)}/" +
        "${MapTileIndex.getX(pMapTileIndex)}/" +
        "${MapTileIndex.getY(pMapTileIndex)}$mImageFilenameEnding"
}

@Composable
actual fun CityMap(
    modifier: Modifier,
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,
    onCourtTap: (Court) -> Unit
) {
    val onCourtTapRef = rememberUpdatedState(onCourtTap)
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = "RacketMatch/1.0"
            MapView(ctx).apply {
                setTileSource(CartoDarkTiles)
                setMultiTouchControls(true)
                controller.setZoom(12.0)
                controller.setCenter(GeoPoint(52.2297, 21.0122))
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                mapViewRef.value = this
            }
        }
    )

    LaunchedEffect(courts, sessionCountByCourt, mapViewRef.value) {
        val mapView = mapViewRef.value ?: return@LaunchedEffect
        mapView.overlays.removeAll { it is Marker }

        for (court in courts) {
            val sessionCount = sessionCountByCourt[court.id] ?: 0
            val marker = Marker(mapView).apply {
                position = GeoPoint(court.lat, court.lng)
                title = court.name
                icon = createCourtMarkerDrawable(court, sessionCount)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    onCourtTapRef.value(court)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }
}

private fun createCourtMarkerDrawable(court: Court, sessionCount: Int): android.graphics.drawable.BitmapDrawable {
    val size = 360
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val hasSessions = sessionCount > 0
    val limeColor = android.graphics.Color.parseColor("#D4FF00")
    val bgColor = if (hasSessions) limeColor else android.graphics.Color.parseColor("#1A2A1A")
    val ringColor = if (hasSessions) limeColor else android.graphics.Color.parseColor("#D4FF00")
    val textColor = if (hasSessions) android.graphics.Color.parseColor("#0A0A0A") else android.graphics.Color.parseColor("#D4FF00")

    val cx = size / 2f
    val cy = size / 2f

    // Outermost glow
    val glow2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor; alpha = 20; style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 172f, glow2Paint)

    // Outer glow ring
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor; alpha = 45; style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 145f, glowPaint)

    // Mid ring
    val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor; alpha = 80; style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 118f, midPaint)

    // Main circle
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor; style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 96f, circlePaint)

    // Border stroke
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor; style = Paint.Style.STROKE; strokeWidth = 6f
    }
    canvas.drawCircle(cx, cy, 96f, strokePaint)

    // Sport letter
    val sportLetter = when {
        court.sports.contains(Sport.PADEL) && !court.sports.contains(Sport.TENNIS) -> "P"
        court.sports.contains(Sport.TENNIS) && !court.sports.contains(Sport.PADEL) -> "T"
        else -> "TP"
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = if (sportLetter.length > 1) 64f else 80f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(sportLetter, cx, textY, textPaint)

    // Session count badge
    if (sessionCount > 0) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FF6B35"); style = Paint.Style.FILL
        }
        canvas.drawCircle(cx + 76f, cy - 76f, 46f, badgePaint)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val badgeY = (cy - 76f) - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
        canvas.drawText("$sessionCount", cx + 76f, badgeY, badgeTextPaint)
    }

    return android.graphics.drawable.BitmapDrawable(null, bitmap)
}
