package com.techteam.vpn.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orthographic projection of the globe seen from straight above (centerLat, centerLon).
 * Screen coordinates are in -1..1 with y up; angles are in degrees.
 */
class GlobeMath(centerLat: Double, centerLon: Double) {
    private val lat0 = Math.toRadians(centerLat)
    private val lon0 = Math.toRadians(centerLon)
    private val sinLat0 = sin(lat0)
    private val cosLat0 = cos(lat0)

    /** The point of the sphere under screen point (x, y), or null outside the disc. Returns (lat, lon). */
    fun unproject(x: Double, y: Double): Pair<Double, Double>? {
        val out = DoubleArray(2)
        return if (unprojectInto(x, y, out)) out[0] to out[1] else null
    }

    /** Allocation-free [unproject] for the per-pixel render loop: writes lat, lon into [out]. */
    fun unprojectInto(x: Double, y: Double, out: DoubleArray): Boolean {
        val d2 = x * x + y * y
        if (d2 > 1) return false
        val z = sqrt(1 - d2)
        out[0] = Math.toDegrees(asin((z * sinLat0 + y * cosLat0).coerceIn(-1.0, 1.0)))
        out[1] = normalize(Math.toDegrees(lon0 + atan2(x, z * cosLat0 - y * sinLat0)))
        return true
    }

    /** Screen point of (lat, lon), or null when it is on the far side of the globe. */
    fun project(lat: Double, lon: Double): Pair<Double, Double>? {
        val la = Math.toRadians(lat)
        val dl = Math.toRadians(lon) - lon0
        val cosC = sinLat0 * sin(la) + cosLat0 * cos(la) * cos(dl)
        if (cosC < 0) return null
        return cos(la) * sin(dl) to cosLat0 * sin(la) - sinLat0 * cos(la) * cos(dl)
    }

    companion object {
        fun normalize(lon: Double): Double = ((lon + 180) % 360 + 360) % 360 - 180

        /** Points along the great circle from a to b, for the line between the two pins. */
        fun greatCircle(a: Pair<Double, Double>, b: Pair<Double, Double>, steps: Int = 48): List<Pair<Double, Double>> {
            fun vec(p: Pair<Double, Double>): DoubleArray {
                val la = Math.toRadians(p.first)
                val lo = Math.toRadians(p.second)
                return doubleArrayOf(cos(la) * cos(lo), cos(la) * sin(lo), sin(la))
            }
            val va = vec(a)
            val vb = vec(b)
            val dot = (va[0] * vb[0] + va[1] * vb[1] + va[2] * vb[2]).coerceIn(-1.0, 1.0)
            val omega = kotlin.math.acos(dot)
            if (omega < 1e-6) return listOf(a, b)
            return (0..steps).map { i ->
                val t = i.toDouble() / steps
                val ka = sin((1 - t) * omega) / sin(omega)
                val kb = sin(t * omega) / sin(omega)
                val v = DoubleArray(3) { ka * va[it] + kb * vb[it] }
                Math.toDegrees(atan2(v[2], sqrt(v[0] * v[0] + v[1] * v[1]))) to Math.toDegrees(atan2(v[1], v[0]))
            }
        }
    }
}
