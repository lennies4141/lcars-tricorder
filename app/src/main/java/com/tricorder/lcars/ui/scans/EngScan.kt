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
import com.tricorder.lcars.ui.holo.PowerCell
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType

@Composable
fun EngScan(s: TricorderState, active: Boolean) {
    val b = s.battery

    SectionHeader("POWER CELL CORE", LcarsColors.Orange)
    PowerCell(s, active, Modifier.fillMaxWidth().height(250.dp))

    SectionHeader("POWER CELL RESERVE", LcarsColors.Yellow)
    Text(
        b?.levelPercent?.takeIf { it >= 0 }?.let { "$it%" } ?: "---%",
        style = LcarsType.BigReadout,
    )
    val pct = (b?.levelPercent ?: 0) / 100f
    SegmentGauge(
        pct,
        when {
            pct < 0.15f -> LcarsColors.Red
            pct < 0.35f -> LcarsColors.Sunset
            else -> LcarsColors.Yellow
        },
    )

    SectionHeader("THERMAL / ELECTRICAL", LcarsColors.Orange)
    Readout("CELL TEMP", b?.temperatureC?.fmt(1), "°C", tempColor(b?.temperatureC))
    Readout("CELL TEMP", b?.temperatureC?.let { (it * 9f / 5f + 32f).fmt(1) }, "°F", tempColor(b?.temperatureC))
    Readout("VOLTAGE", b?.voltageV?.fmt(3), "V", LcarsColors.Ice)

    SectionHeader("POWER SYSTEMS STATUS", LcarsColors.Peach)
    Readout("STATE", b?.status)
    Readout("HEALTH", b?.health, valueColor = if (b?.health == "NOMINAL") LcarsColors.Ice else LcarsColors.Red)
    Readout("SOURCE", b?.powerSource)
    Readout("CHEMISTRY", b?.technology)
}

private fun tempColor(c: Float?) = when {
    c == null -> LcarsColors.Text
    c >= 42f -> LcarsColors.Red
    c >= 37f -> LcarsColors.Sunset
    else -> LcarsColors.Text
}
