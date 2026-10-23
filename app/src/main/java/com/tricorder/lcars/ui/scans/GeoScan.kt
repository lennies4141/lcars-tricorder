package com.tricorder.lcars.ui.scans

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.sensors.Vec3
import com.tricorder.lcars.ui.Readout
import com.tricorder.lcars.ui.SectionHeader
import com.tricorder.lcars.ui.fmt
import com.tricorder.lcars.ui.sfmt
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeoScan(s: TricorderState) {
    SectionHeader("MAGNETIC BEARING", LcarsColors.Orange)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CompassRose(s.headingDeg, Modifier.weight(1f).aspectRatio(1f))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(s.headingDeg?.let { "%03d°".format(it.toInt()) } ?: "---°", style = LcarsType.BigReadout)
            Text(s.headingDeg?.let { cardinal(it) } ?: "NO FIX", style = LcarsType.Title.copy(color = LcarsColors.Peach))
        }
    }
    Readout("PITCH", s.pitchDeg?.sfmt(1, 6), "°")
    Readout("ROLL", s.rollDeg?.sfmt(1, 6), "°")

    SectionHeader("MAGNETOMETER  µT", LcarsColors.Sunset)
    VectorBlock(s.magField, 2, "µT")

    SectionHeader("ACCELEROMETER  m/s²", LcarsColors.Lilac)
    VectorBlock(s.accel, 2, "m/s²")

    SectionHeader("GYROSCOPE  rad/s", LcarsColors.Periwinkle)
    VectorBlock(s.gyro, 3, "rad/s")
}

@Composable
internal fun VectorBlock(v: Vec3?, decimals: Int, unit: String) {
    Column {
        Readout("X-AXIS", v?.x?.sfmt(decimals))
        Readout("Y-AXIS", v?.y?.sfmt(decimals))
        Readout("Z-AXIS", v?.z?.sfmt(decimals))
        Readout("MAGNITUDE", v?.magnitude?.fmt(decimals), unit, LcarsColors.Yellow)
    }
}

private fun cardinal(deg: Float): String {
    val names = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return names[(((deg + 11.25f) % 360f) / 22.5f).toInt().coerceIn(0, 15)]
}

@Composable
private fun CompassRose(heading: Float?, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 * 0.92f
            drawCircle(LcarsColors.Orange, r, c, style = Stroke(width = r * 0.05f))
            drawCircle(LcarsColors.Purple, r * 0.62f, c, style = Stroke(width = r * 0.02f))

            // Dial rotates opposite the heading so north stays pointed at magnetic north.
            rotate(-(heading ?: 0f), c) {
                for (i in 0 until 72) {
                    val major = i % 9 == 0
                    val a = Math.toRadians(i * 5.0 - 90.0)
                    val inner = if (major) r * 0.74f else r * 0.84f
                    drawLine(
                        if (major) LcarsColors.Yellow else LcarsColors.Tan,
                        Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                        Offset(c.x + r * 0.94f * cos(a).toFloat(), c.y + r * 0.94f * sin(a).toFloat()),
                        strokeWidth = if (major) r * 0.035f else r * 0.015f,
                    )
                }
                // North marker triangle
                val n = Path().apply {
                    moveTo(c.x, c.y - r * 0.58f)
                    lineTo(c.x - r * 0.12f, c.y - r * 0.32f)
                    lineTo(c.x + r * 0.12f, c.y - r * 0.32f)
                    close()
                }
                drawPath(n, LcarsColors.Red)
            }
            // Fixed lubber line (device "forward" direction)
            drawLine(LcarsColors.Ice, Offset(c.x, c.y - r), Offset(c.x, c.y - r * 0.7f), strokeWidth = r * 0.05f)
            drawCircle(LcarsColors.Orange, r * 0.08f, c)
        }
    }
}
