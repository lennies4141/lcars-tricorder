package com.tricorder.lcars.ui.scans

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.Readout
import com.tricorder.lcars.ui.SectionHeader
import com.tricorder.lcars.ui.SegmentGauge
import com.tricorder.lcars.ui.fmt
import com.tricorder.lcars.ui.holo.TerrainScan
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType
import kotlin.math.log10

@Composable
fun MeteoScan(s: TricorderState, active: Boolean, onHoloTap: () -> Unit) {
    SectionHeader("TOPOGRAPHIC SURVEY", LcarsColors.Blue)
    TerrainScan(s, active, Modifier.fillMaxWidth().height(230.dp), onHoloTap)
    SectionHeader("ATMOSPHERIC PRESSURE", LcarsColors.Periwinkle)
    Text(s.pressureHpa?.fmt(2) ?: "-----.--", style = LcarsType.BigReadout.copy(color = LcarsColors.Ice))
    Readout("HECTOPASCAL", s.pressureHpa?.fmt(2), "hPa")
    Readout("INCHES HG", s.pressureHpa?.let { (it * 0.0295300f).fmt(2) }, "inHg")
    // 950–1050 hPa covers essentially all surface weather.
    SegmentGauge(((s.pressureHpa ?: 950f) - 950f) / 100f, LcarsColors.Periwinkle)

    SectionHeader("ALTIMETRY  (STD ATM REF)", LcarsColors.Blue)
    Readout("ALTITUDE", s.altitudeM?.fmt(1), "m")
    Readout("ALTITUDE", s.altitudeM?.let { (it * 3.28084f).fmt(0) }, "ft")

    SectionHeader("PHOTOMETRIC", LcarsColors.Yellow)
    Readout("ILLUMINANCE", s.lux?.fmt(1), "lx", LcarsColors.Yellow)
    Readout("CLASS", s.lux?.let { lightClass(it) })
    // Log scale: 1 lx .. 100,000 lx
    SegmentGauge(s.lux?.let { log10(it.coerceAtLeast(1f)) / 5f } ?: 0f, LcarsColors.Yellow)
}

internal fun lightClass(lux: Float): String = when {
    lux < 1f -> "DARKNESS"
    lux < 50f -> "DIM"
    lux < 500f -> "INTERIOR"
    lux < 2_000f -> "BRIGHT INTERIOR"
    lux < 10_000f -> "OVERCAST"
    lux < 30_000f -> "DAYLIGHT"
    else -> "DIRECT STELLAR"
}
