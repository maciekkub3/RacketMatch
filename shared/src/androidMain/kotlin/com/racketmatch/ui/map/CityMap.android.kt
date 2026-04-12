package com.racketmatch.ui.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.*
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.Sport
import kotlin.math.abs

private data class CourtClusterItem(
    val court: Court,
    val sessionCount: Int
) : ClusterItem {
    override fun getPosition() = LatLng(court.lat, court.lng)
    override fun getTitle(): String = court.name
    override fun getSnippet(): String? = null
    override fun getZIndex(): Float = 0f
}


private const val DAY_STYLE = """[
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"road","elementType":"labels","stylers":[{"visibility":"off"}]},
  {"featureType":"administrative.neighborhood","elementType":"labels.text","stylers":[{"visibility":"on"}]},
  {"featureType":"administrative.locality","elementType":"labels.text","stylers":[{"visibility":"on"}]}
]"""

private const val NIGHT_STYLE = """[
  {"elementType":"geometry","stylers":[{"color":"#1a1a18"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#7a7a6a"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1a1a18"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2e2e2c"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#1a1a18"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#363620"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1f1f10"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0a0a0a"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#2a2a28"}]},
  {"featureType":"landscape","stylers":[{"color":"#1a1a18"}]}
]"""

private val CITY_CENTERS = mapOf(
    "Warszawa" to LatLng(52.2297, 21.0122),
    "Poznań"   to LatLng(52.4064, 16.9252),
    "Wrocław"  to LatLng(51.1079, 17.0385),
    "Szczecin" to LatLng(53.4285, 14.5528)
)

@Composable
actual fun CityMap(
    modifier: Modifier,
    courts: List<Court>,
    sessionCountByCourt: Map<String, Int>,
    onCourtTap: (Court) -> Unit,
    city: String,
    isDark: Boolean
) {
    val context = LocalContext.current
    val onCourtTapRef = rememberUpdatedState(onCourtTap)
    val cityCenter = CITY_CENTERS[city] ?: LatLng(52.2297, 21.0122)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(cityCenter, 12f)
    }

    LaunchedEffect(city) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(cityCenter, 12f))
    }

    val items = remember(courts, sessionCountByCourt) {
        courts.map { court ->
            CourtClusterItem(court = court, sessionCount = sessionCountByCourt[court.id] ?: 0)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapStyleOptions = if (isDark) MapStyleOptions(NIGHT_STYLE) else MapStyleOptions(DAY_STYLE)
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        MapEffect(items) { map ->
            map.clear()

            val clusterManager = ClusterManager<CourtClusterItem>(context, map)

            clusterManager.algorithm = NonHierarchicalDistanceBasedAlgorithm<CourtClusterItem>().apply {
                maxDistanceBetweenClusteredItems = 40
            }

            val renderer = object : DefaultClusterRenderer<CourtClusterItem>(context, map, clusterManager) {

                override fun onBeforeClusterItemRendered(item: CourtClusterItem, markerOptions: MarkerOptions) {
                    markerOptions
                        .icon(BitmapDescriptorFactory.fromBitmap(courtBitmap(item.court, item.sessionCount, isDark)))
                        .anchor(0.5f, 0.5f)
                }

                override fun onBeforeClusterRendered(cluster: Cluster<CourtClusterItem>, markerOptions: MarkerOptions) {
                    val sports = cluster.items.flatMap { it.court.sports }.toSet()
                    val hasSession = cluster.items.any { it.sessionCount > 0 }
                    markerOptions
                        .icon(BitmapDescriptorFactory.fromBitmap(clusterBitmap(sports, cluster.size, hasSession, isDark)))
                        .anchor(0.5f, 0.5f)
                }

                override fun onClusterRendered(cluster: Cluster<CourtClusterItem>, marker: com.google.android.gms.maps.model.Marker) {
                    val sports = cluster.items.flatMap { it.court.sports }.toSet()
                    val hasSession = cluster.items.any { it.sessionCount > 0 }
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(clusterBitmap(sports, cluster.size, hasSession, isDark)))
                }

                // Cluster as soon as 2+ markers are close
                override fun shouldRenderAsCluster(cluster: Cluster<CourtClusterItem>) = cluster.size >= 2
            }

            clusterManager.renderer = renderer

            // Re-cluster in real time while pinching — not just on idle
            var lastZoom = map.cameraPosition.zoom
            map.setOnCameraMoveListener {
                val zoom = map.cameraPosition.zoom
                if (abs(zoom - lastZoom) > 0.15f) {
                    lastZoom = zoom
                    clusterManager.cluster()
                }
            }
            map.setOnCameraIdleListener { clusterManager.onCameraIdle() }

            clusterManager.setOnClusterItemClickListener { item ->
                onCourtTapRef.value(item.court)
                true
            }

            clusterManager.setOnClusterClickListener { cluster ->
                val boundsBuilder = LatLngBounds.builder()
                cluster.items.forEach { boundsBuilder.include(it.position) }
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                true
            }

            clusterManager.addItems(items)
            clusterManager.cluster()
        }
    }
}

// ── Bitmaps ───────────────────────────────────────────────────────────────────

private fun circleBg(isDark: Boolean) =
    if (isDark) android.graphics.Color.parseColor("#2A2A28")
    else android.graphics.Color.parseColor("#006633")

private fun circleBgAlt(isDark: Boolean) =
    if (isDark) android.graphics.Color.parseColor("#1E1E1C")
    else android.graphics.Color.parseColor("#004d26")

private fun courtBitmap(court: Court, sessionCount: Int, isDark: Boolean): Bitmap {
    val hasSession = sessionCount > 0
    val hasTennis = court.sports.contains(Sport.TENNIS)
    val hasPadel  = court.sports.contains(Sport.PADEL)
    return if (hasTennis && hasPadel) {
        doubleCircleBitmap(1, hasSession, isDark)
    } else {
        val emoji = if (hasPadel) "🏸" else "🎾"
        singleCircleBitmap(emoji, 1, hasSession, isDark)
    }
}

private fun clusterBitmap(sports: Set<Sport>, count: Int, hasSession: Boolean, isDark: Boolean): Bitmap {
    val hasTennis = sports.contains(Sport.TENNIS)
    val hasPadel  = sports.contains(Sport.PADEL)
    return if (hasTennis && hasPadel) {
        doubleCircleBitmap(count, hasSession, isDark)
    } else {
        val emoji = if (hasPadel) "🏸" else "🎾"
        singleCircleBitmap(emoji, count, hasSession, isDark)
    }
}

/** Single sport — one circle with emoji + optional count badge. */
private fun singleCircleBitmap(emoji: String, count: Int, hasSession: Boolean, isDark: Boolean): Bitmap {
    val r = 38f
    val ringExtra = if (hasSession) 24f else 0f
    val badgeR = 13f
    val pad = badgeR
    val totalW = (r * 2 + pad + ringExtra).toInt()
    val totalH = (r * 2 + pad + ringExtra).toInt()

    val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = r + ringExtra / 2f
    val cy = r + pad / 2f + ringExtra / 2f

    if (hasSession) drawRing(canvas, cx, cy, r)
    drawCircleWithShadow(canvas, cx, cy, r, circleBg(isDark))

    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = r * 0.85f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(emoji, cx, cy - (emojiPaint.descent() + emojiPaint.ascent()) / 2f, emojiPaint)

    if (count > 1) {
        val limeColor = android.graphics.Color.parseColor("#9EC80A")
        drawBadge(canvas, cx + r * 0.65f, cy - r * 0.65f, badgeR, count, limeColor)
    }

    return bitmap
}

/** Tennis + Padel — two overlapping circles + optional count badge. */
private fun doubleCircleBitmap(count: Int, hasSession: Boolean, isDark: Boolean): Bitmap {
    val r = 33f
    val overlap = r * 0.55f
    val badgeR = 13f
    val ringExtra = if (hasSession) 24f else 0f
    val padTop = badgeR
    val leftCx = r + badgeR * 0.3f + ringExtra / 2f
    val rightCx = leftCx + r * 2f - overlap
    val totalW = (rightCx + r + badgeR * 0.8f + ringExtra / 2f).toInt()
    val totalH = (r * 2f + padTop + ringExtra).toInt()

    val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cy = r + padTop / 2f + ringExtra / 2f

    if (hasSession) {
        val midCx = (leftCx + rightCx) / 2f
        val halfWidth = (rightCx - leftCx) / 2f + r
        drawRing(canvas, midCx, cy, halfWidth)
    }

    // Right circle drawn first so left circle is on top
    drawCircleWithShadow(canvas, rightCx, cy, r, circleBgAlt(isDark))
    drawCircleWithShadow(canvas, leftCx, cy, r, circleBg(isDark))

    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = r * 0.82f
        textAlign = Paint.Align.CENTER
    }
    val offsetY = -(emojiPaint.descent() + emojiPaint.ascent()) / 2f
    canvas.drawText("🎾", leftCx, cy + offsetY, emojiPaint)
    canvas.drawText("🏸", rightCx, cy + offsetY, emojiPaint)

    if (count > 1) {
        val limeColor = android.graphics.Color.parseColor("#9EC80A")
        drawBadge(canvas, rightCx + r * 0.62f, cy - r * 0.62f, badgeR, count, limeColor)
    }

    return bitmap
}

private fun drawRing(canvas: Canvas, cx: Float, cy: Float, r: Float) {
    val limeColor = android.graphics.Color.parseColor("#9EC80A")
    canvas.drawCircle(cx, cy, r + 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = limeColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
    })
}

private fun drawCircleWithShadow(canvas: Canvas, cx: Float, cy: Float, r: Float, bgColor: Int) {
    // Soft shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(50, 0, 0, 0)
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawCircle(cx, cy + 3f, r, shadowPaint)

    // Main circle
    canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    })
}

private fun drawBadge(canvas: Canvas, bx: Float, by: Float, r: Float, value: Int, color: Int) {
    canvas.drawCircle(bx, by, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    })
    val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = r * 1.1f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("$value", bx, by - (bp.descent() + bp.ascent()) / 2f, bp)
}
