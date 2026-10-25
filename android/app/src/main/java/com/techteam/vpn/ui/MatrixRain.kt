package com.techteam.vpn.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private const val GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄ0123456789TECHVPN"

/**
 * The easter egg: green "Matrix" characters raining over the globe. Ten quick taps on the
 * globe turn it on and off. Each column's speed comes from its index, so nothing is stored.
 */
@Composable
fun MatrixRain(modifier: Modifier = Modifier) {
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { time = it } }
    val paint = remember {
        Paint().apply { typeface = Typeface.MONOSPACE; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    }
    Canvas(modifier) {
        drawRect(Color.Black.copy(alpha = 0.35f))
        val cell = 16.dp.toPx()
        paint.textSize = 14.sp.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val t = time / 1000.0
        drawIntoCanvas { canvas ->
            for (c in 0 until columns) {
                val rng = Random(c * 7919)
                val speed = 6 + rng.nextInt(10)
                val offset = rng.nextInt(1000)
                val length = 8 + rng.nextInt(16)
                val head = ((t * speed + offset) % (rows + length)).toInt()
                for (k in 0 until length) {
                    val row = head - k
                    if (row < 0 || row >= rows) continue
                    // Characters flicker: each cell changes glyph a few times a second.
                    val g = GLYPHS[((row * 31 + c * 17 + (t * 8).toLong()) % GLYPHS.length).toInt()]
                    val alpha = if (k == 0) 1f else 0.85f * (1 - k.toFloat() / length)
                    paint.color = if (k == 0) android.graphics.Color.WHITE else android.graphics.Color.rgb(26, 255, 90)
                    paint.alpha = (alpha * 255).toInt()
                    canvas.nativeCanvas.drawText(g.toString(), c * cell + cell / 2, row * cell + cell, paint)
                }
            }
        }
    }
}
