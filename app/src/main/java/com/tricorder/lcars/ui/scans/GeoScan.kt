package com.tricorder.lcars.ui.scans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.sensors.Vec3
import com.tricorder.lcars.ui.holo.GyroGlobe
import com.tricorder.lcars.ui.Readout
import com.tricorder.lcars.ui.SectionHeader
import com.tricorder.lcars.ui.fmt
import com.tricorder.lcars.ui.sfmt
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType

@Composable
fun GeoScan(s: TricorderState, active: Boolean, onHoloTap: () -> Unit) {
    SectionHeader("GYRO-STABILIZED GEOSPHERE", LcarsColors.Orange)
    GyroGlobe(s, active, Modifier.fillMaxWidth().aspectRatio(1f), onHoloTap)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(s.headingDeg?.let { "%03d°".format(it.toInt()) } ?: "---°", style = LcarsType.BigReadout)
        Spacer(Modifier.weight(1f))
        Text(s.headingDeg?.let { cardinal(it) } ?: "NO FIX", style = LcarsType.Title.copy(color = LcarsColors.Peach))
    }
    Readout("PITCH", s.pitchDeg?.sfmt(1, 6), "°")
    Readout("ROLL", s.rollDeg?.sfmt(1, 6), "°")
    Readout("FIELD", s.magField?.magnitude?.let { fieldClass(it) }, valueColor = fieldColor(s.magField?.magnitude))

    SectionHeader("MAGNETOMETER  µT", LcarsColors.Sunset)
    VectorBlock(s.magField, 2, "µT")

    SectionHeader("ACCELEROMETER  m/s²", LcarsColors.Lilac)
    VectorBlock(s.accel, 2, "m/s²")

    SectionHeader("GYROSCOPE  rad/s", LcarsColors.Periwinkle)
    VectorBlock(s.gyro, 3, "rad/s")
}

/** Earth's field is ~25–65 µT; much higher means nearby ferrous metal or magnets. */
fun fieldClass(ut: Float): String = when {
    ut < 20f -> "SHIELDED"
    ut <= 70f -> "NOMINAL GEOMAG"
    ut <= 150f -> "FERROUS TRACE"
    else -> "STRONG ANOMALY"
}

private fun fieldColor(ut: Float?) = when {
    ut == null -> LcarsColors.Text
    ut > 150f -> LcarsColors.Red
    ut > 70f -> LcarsColors.Sunset
    else -> LcarsColors.Ice
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

internal fun cardinal(deg: Float): String {
    val names = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return names[(((deg + 11.25f) % 360f) / 22.5f).toInt().coerceIn(0, 15)]
}
