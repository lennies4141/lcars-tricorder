package com.tricorder.lcars.ui.scans

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tricorder.lcars.sensors.SensorHub
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.Readout
import com.tricorder.lcars.ui.SectionHeader
import com.tricorder.lcars.ui.fmt
import com.tricorder.lcars.ui.theme.LcarsColors

/**
 * Phones have no true biosensors, so BIO-SCAN reads "kinetic life signs":
 * gravity-free linear acceleration (motion/tremor of whoever holds the device)
 * plus the proximity sensor as a contact detector.
 */
@Composable
fun BioScan(s: TricorderState) {
    SectionHeader("KINETIC WAVEFORM", LcarsColors.Lilac)
    MotionWaveform(s.motionHistory)
    val m = s.linearAccel?.magnitude
    Readout("MOTION", m?.fmt(3), "m/s²", LcarsColors.Lilac)
    Readout("ACTIVITY", m?.let { activity(it) })
    Readout("PEAK WINDOW", s.motionHistory.maxOrNull()?.fmt(3), "m/s²")

    SectionHeader("PROXIMITY CONTACT", LcarsColors.Purple)
    val p = s.proximityCm
    val max = s.proximityMaxCm ?: 5f
    Readout("RANGE", p?.fmt(1), "cm")
    Readout(
        "STATUS",
        p?.let { if (it < max) "CONTACT" else "CLEAR" },
        valueColor = if (p != null && p < max) LcarsColors.Red else LcarsColors.Ice,
    )
}

private fun activity(m: Float): String = when {
    m < 0.08f -> "STILL"
    m < 0.6f -> "MINOR TREMOR"
    m < 3f -> "ACTIVE"
    else -> "VIOLENT"
}

@Composable
private fun MotionWaveform(history: List<Float>) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(LcarsColors.Black),
    ) {
        val w = size.width
        val h = size.height
        // Grid
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(LcarsColors.Dim, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        for (i in 1..9) {
            val x = w * i / 10f
            drawLine(LcarsColors.Dim.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        }
        if (history.size < 2) return@Canvas
        val scale = (history.maxOrNull() ?: 1f).coerceAtLeast(1.0f)
        val step = w / (SensorHub.HISTORY_SIZE - 1)
        val startX = w - step * (history.size - 1)
        val path = Path()
        history.forEachIndexed { i, v ->
            val x = startX + i * step
            val y = h - (v / scale) * h * 0.9f - h * 0.05f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, LcarsColors.Lilac, style = Stroke(width = 3f))
        drawRect(LcarsColors.Purple, style = Stroke(width = 2f))
    }
}
