package com.techteam.vpn.ui

import android.graphics.Color as AndroidColor
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** A labelled point on the map; [active] pins are drawn in the accent color. */
data class MapPin(val lat: Double, val lon: Double, val label: String, val active: Boolean)

/** OpenFreeMap vector styles: free, no key; attribution is shown by the map. */
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/**
 * A real map (MapLibre with OpenFreeMap tiles) that can be dragged, pinched and rotated. It
 * flies to [centerLat], [centerLon] when the place changes; [line] joins two pins along the
 * route through the VPN. Labels are Compose views kept over their points, so flags render.
 */
@Composable
fun WorldMap(
    centerLat: Double,
    centerLon: Double,
    pins: List<MapPin>,
    line: Pair<MapPin, MapPin>?,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Bumped on every camera move so the labels follow their points.
    var cameraTick by remember { mutableIntStateOf(0) }
    val accent = Brand.Cyan.toArgb()
    val green = Brand.Green.toArgb()

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Leaving the home screen: wind the map down in order before destroying it.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isCompassEnabled = true
                compassGravity = Gravity.TOP or Gravity.END
                setCompassMargins(0, 220, 32, 0)
                attributionGravity = Gravity.TOP or Gravity.START
                logoGravity = Gravity.TOP or Gravity.START
                setAttributionMargins(220, 220, 0, 0)
                setLogoMargins(32, 220, 0, 0)
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(centerLat, centerLon)).zoom(3.0).build()
            m.addOnCameraMoveListener { cameraTick++ }
            map = m
        }
    }

    // A new style replaces the layers, so they are added again for it.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE)) { s ->
            s.addSource(GeoJsonSource(ROUTE_SOURCE))
            s.addSource(GeoJsonSource(PINS_SOURCE))
            s.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor(accent),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
                ),
            )
            s.addLayer(
                CircleLayer(PINS_LAYER, PINS_SOURCE).withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
            )
            style = s
            cameraTick++
        }
    }

    LaunchedEffect(style, pins, line) {
        val s = style ?: return@LaunchedEffect
        val points = pins.map { pin ->
            Feature.fromGeometry(Point.fromLngLat(pin.lon, pin.lat)).apply {
                addStringProperty("color", colorString(if (pin.active) green else AndroidColor.WHITE))
            }
        }
        s.getSourceAs<GeoJsonSource>(PINS_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(points))
        val route = line?.let { (a, b) ->
            listOf(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(a.lon, a.lat), Point.fromLngLat(b.lon, b.lat)))))
        }.orEmpty()
        s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(route))
    }

    // Fly to the place only when it changes, so the user's own panning is left alone.
    LaunchedEffect(map, centerLat, centerLon) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLon), 3.5), 1400)
    }

    BoxWithConstraints(modifier.background(Color.Black)) {
        AndroidView({ mapView }, Modifier.fillMaxSize())
        val m = map
        if (m != null && style != null) {
            val density = LocalDensity.current
            val points = remember(cameraTick, pins, m) { pins.map { m.projection.toScreenLocation(LatLng(it.lat, it.lon)) } }
            pins.zip(points).forEach { (pin, p) ->
                Box(Modifier.offset { IntOffset(p.x.toInt(), p.y.toInt() - with(density) { 40.dp.roundToPx() }) }) {
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
                                if (pin.active) Color(green).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private const val PINS_SOURCE = "pins"
private const val PINS_LAYER = "pins-layer"
private const val ROUTE_SOURCE = "route"
private const val ROUTE_LAYER = "route-layer"

private fun colorString(argb: Int): String =
    "rgba(%d,%d,%d,1)".format(AndroidColor.red(argb), AndroidColor.green(argb), AndroidColor.blue(argb))
