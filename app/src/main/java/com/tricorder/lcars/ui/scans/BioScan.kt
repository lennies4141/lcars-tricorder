package com.tricorder.lcars.ui.scans

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.Readout
import com.tricorder.lcars.ui.SectionHeader
import com.tricorder.lcars.ui.fmt
import com.tricorder.lcars.ui.holo.DnaHelix
import com.tricorder.lcars.ui.holo.MotionWaterfall
import com.tricorder.lcars.ui.theme.LcarsColors

/**
 * Phones have no true biosensors, so BIO-SCAN reads "kinetic life signs":
 * gravity-free linear acceleration (motion/tremor of whoever holds the device)
 * plus the proximity sensor as a contact detector.
 */
@Composable
fun BioScan(s: TricorderState, active: Boolean) {
    SectionHeader("GENETIC SEQUENCER", LcarsColors.Lilac)
    DnaHelix(s, active, Modifier.fillMaxWidth().height(150.dp))
    SectionHeader("KINETIC WATERFALL", LcarsColors.Purple)
    MotionWaterfall(s, Modifier.fillMaxWidth().height(190.dp))
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

internal fun activity(m: Float): String = when {
    m < 0.08f -> "STILL"
    m < 0.6f -> "MINOR TREMOR"
    m < 3f -> "ACTIVE"
    else -> "VIOLENT"
}
