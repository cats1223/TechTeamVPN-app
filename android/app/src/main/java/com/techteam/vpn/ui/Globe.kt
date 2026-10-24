package com.techteam.vpn.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techteam.vpn.core.GlobeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

/** A labelled point on the globe; [active] pins are drawn in the accent color. */
data class GlobePin(val lat: Double, val lon: Double, val label: String, val active: Boolean)

/**
 * The Earth as seen from space, turned to [centerLat], [centerLon]; it rotates smoothly when the
 * center changes. [line] joins two pins along the great circle, like the route through the VPN.
 */
@Composable
fun Globe(
    centerLat: Double,
    centerLon: Double,
    pins: List<GlobePin>,
    line: Pair<GlobePin, GlobePin>?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lat = remember { Animatable(centerLat.toFloat()) }
    val lon = remember { Animatable(centerLon.toFloat()) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val accent = Brand.Cyan
    val green = Brand.Green

    LaunchedEffect(centerLat, centerLon) {
        // Turn the short way round: from 170° to -170° is 20°, not 340°.
        val delta = GlobeMath.normalize(centerLon - lon.value).toFloat()
        launch { lat.animateTo(centerLat.toFloat(), tween(1400, easing = FastOutSlowInEasing)) }
        lon.animateTo(lon.value + delta, tween(1400, easing = FastOutSlowInEasing))
        lon.snapTo(GlobeMath.normalize(lon.value.toDouble()).toFloat())
    }
    LaunchedEffect(Unit) {
        val texture = withContext(Dispatchers.IO) { EarthTexture.load(context) }
        // Rendering can be slower than the animation; conflate skips to the newest position.
        snapshotFlow { lat.value to lon.value }.conflate().collect { (la, lo) ->
            frame = withContext(Dispatchers.Default) { texture.render(RENDER_SIZE, la.toDouble(), lo.toDouble()) }.asImageBitmap()
        }
    }

    BoxWithConstraints(modifier.background(Color(0xFF02040A)).clipToBounds()) {
        val density = LocalDensity.current
        // A little wider than the screen, as if the camera were close, and set slightly above center.
        val diameterPx = with(density) { maxWidth.toPx() } * 1.45f
        val centerX = with(density) { maxWidth.toPx() } / 2
        val centerY = with(density) { maxHeight.toPx() } * 0.36f
        val radius = diameterPx / 2
        val math = GlobeMath(lat.value.toDouble(), lon.value.toDouble())
        fun screen(p: Pair<Double, Double>) = Offset(centerX + (p.first * radius).toFloat(), centerY - (p.second * radius).toFloat())

        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(centerX, centerY), radius = radius * 1.12f,
                ),
                radius = radius * 1.12f, center = Offset(centerX, centerY),
            )
        }
        frame?.let { bitmap ->
            Image(
                bitmap, null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        // The globe is larger than the screen; place it by its center.
                        val placeable = measurable.measure(Constraints.fixed(diameterPx.toInt(), diameterPx.toInt()))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place((centerX - radius).toInt(), (centerY - radius).toInt())
                        }
                    },
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            line?.let { (a, b) ->
                val path = Path()
                var drawing = false
                for (point in GlobeMath.greatCircle(a.lat to a.lon, b.lat to b.lon)) {
                    val p = math.project(point.first, point.second)
                    if (p == null) {
                        drawing = false
                        continue
                    }
                    val o = screen(p)
                    if (drawing) path.lineTo(o.x, o.y) else path.moveTo(o.x, o.y)
                    drawing = true
                }
                drawPath(path, accent, style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
            }
            for (pin in pins) {
                val p = math.project(pin.lat, pin.lon) ?: continue
                val o = screen(p)
                drawCircle((if (pin.active) green else Color.White).copy(alpha = 0.35f), 12.dp.toPx(), o)
                drawCircle(if (pin.active) green else Color.White, 6.dp.toPx(), o)
            }
        }
        for (pin in pins) {
            val p = math.project(pin.lat, pin.lon) ?: continue
            val o = screen(p)
            Box(
                Modifier.offset { IntOffset(o.x.toInt(), o.y.toInt() - with(density) { 46.dp.roundToPx() }) },
            ) {
                Text(
                    pin.label,
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            // Centre the label over the point.
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(-placeable.width / 2, 0) }
                        }
                        .background(
                            if (pin.active) green.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Pixel size of the rendered globe; it is scaled up on screen. */
private const val RENDER_SIZE = 480

/** NASA's Blue Marble (public domain) as an equirectangular pixel array. */
private class EarthTexture(private val pixels: IntArray, private val width: Int, private val height: Int) {
    fun render(size: Int, centerLat: Double, centerLon: Double): Bitmap {
        val math = GlobeMath(centerLat, centerLon)
        val out = IntArray(size * size)
        val point = DoubleArray(2)
        val r = size / 2.0
        for (py in 0 until size) {
            val y = (r - py - 0.5) / r
            for (px in 0 until size) {
                val x = (px + 0.5 - r) / r
                val d2 = x * x + y * y
                if (d2 > 1) continue
                if (!math.unprojectInto(x, y, point)) continue
                val tx = min(((point[1] + 180) / 360 * width).toInt(), width - 1)
                val ty = min(((90 - point[0]) / 180 * height).toInt(), height - 1)
                // Darker towards the edge, so it reads as a sphere.
                val light = 0.45 + 0.55 * sqrt(1 - d2)
                val c = pixels[ty * width + tx]
                val red = ((c shr 16 and 0xFF) * light).toInt()
                val green = ((c shr 8 and 0xFF) * light).toInt()
                val blue = ((c and 0xFF) * light).toInt()
                out[py * size + px] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888)
    }

    companion object {
        @Volatile private var cached: EarthTexture? = null

        fun load(context: Context): EarthTexture = cached ?: run {
            val bitmap = context.assets.open("earth.jpg").use { BitmapFactory.decodeStream(it) }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            EarthTexture(pixels, bitmap.width, bitmap.height).also { cached = it }
        }
    }
}
