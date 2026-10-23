package com.tricorder.lcars.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tricorder.lcars.audio.SoundEngine
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.scans.activity
import com.tricorder.lcars.ui.scans.cardinal
import com.tricorder.lcars.ui.scans.TrackerSim
import com.tricorder.lcars.ui.scans.fieldClass
import com.tricorder.lcars.ui.scans.lightClass
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType
import kotlin.math.log10

data class ScanReport(
    val title: String,
    val lines: List<Pair<String, String>>,
    val verdict: String,
    val anomaly: Boolean,
)

/** Snapshot the live readings into a show-style analysis summary. */
fun buildReport(sub: SubSystem, s: TricorderState, tracker: TrackerSim): ScanReport = when (sub) {
    SubSystem.TRACKER -> {
        val n = tracker.nearest()
        val anomaly = n != null && n.range < 6f
        ScanReport(
            "MOTION TRACKER SWEEP",
            listOf(
                "SIGNALS" to "${tracker.contacts.size}",
                "NEAREST" to (n?.let { "%.1f m".format(it.range) } ?: "NONE"),
                "BEARING" to (n?.let { "%+.0f°".format(tracker.relBearing(it.bearing)) } ?: "--"),
                "MOVEMENT" to (n?.let { if (it.pause > 0f) "HOLDING" else "CLOSING" } ?: "--"),
            ),
            if (anomaly) "MULTIPLE SIGNALS INSIDE PERIMETER" else "SIGNALS TRACKED // KEEP MOVING",
            anomaly,
        )
    }
    SubSystem.GEO -> {
        val b = s.magField?.magnitude
        val anomaly = b != null && b > 150f
        ScanReport(
            "GEOMAGNETIC ANALYSIS",
            listOf(
                "BEARING" to (s.headingDeg?.let { "%03d° %s".format(it.toInt(), cardinal(it)) } ?: "NO FIX"),
                "FIELD" to (b?.let { "%.1f µT".format(it) } ?: "OFFLINE"),
                "CLASS" to (b?.let { fieldClass(it) } ?: "--"),
                "ATTITUDE" to (if (s.pitchDeg != null && s.rollDeg != null) "P%+.0f° R%+.0f°".format(s.pitchDeg, s.rollDeg) else "--"),
            ),
            if (anomaly) "MAGNETIC ANOMALY DETECTED" else "GEOMAGNETIC FIELD NOMINAL",
            anomaly,
        )
    }
    SubSystem.METEO -> {
        val p = s.pressureHpa
        val anomaly = p != null && (p < 970f || p > 1045f)
        ScanReport(
            "ATMOSPHERIC ANALYSIS",
            listOf(
                "PRESSURE" to (p?.let { "%.1f hPa".format(it) } ?: "OFFLINE"),
                "ALTITUDE" to (s.altitudeM?.let { "%.0f ft".format(it * 3.28084f) } ?: "--"),
                "LIGHT" to (s.lux?.let { "%.0f lx".format(it) } ?: "OFFLINE"),
                "CONDITIONS" to (s.lux?.let { lightClass(it) } ?: "--"),
            ),
            if (anomaly) "ATMOSPHERIC PRESSURE EXTREME" else "ATMOSPHERE STABLE // BREATHABLE",
            anomaly,
        )
    }
    SubSystem.BIO -> {
        val peak = s.motionHistory.maxOrNull()
        val anomaly = peak != null && peak > 6f
        ScanReport(
            "KINETIC LIFE-SIGN ANALYSIS",
            listOf(
                "LIFE SIGNS" to "1 // OPERATOR",
                "MOTION" to (s.linearAccel?.magnitude?.let { "%.3f m/s²".format(it) } ?: "OFFLINE"),
                "ACTIVITY" to (s.linearAccel?.magnitude?.let { activity(it) } ?: "--"),
                "PEAK" to (peak?.let { "%.2f m/s²".format(it) } ?: "--"),
            ),
            if (anomaly) "VIOLENT KINETIC EVENT" else "VITAL MOTION WITHIN NORMS",
            anomaly,
        )
    }
    SubSystem.ENG -> {
        val b = s.battery
        val hot = (b?.temperatureC ?: 0f) >= 42f
        val low = (b?.levelPercent ?: 100) in 0..14
        ScanReport(
            "POWER SYSTEMS ANALYSIS",
            listOf(
                "RESERVE" to (b?.levelPercent?.let { "$it%" } ?: "OFFLINE"),
                "CELL TEMP" to (b?.temperatureC?.let { "%.1f°C".format(it) } ?: "--"),
                "VOLTAGE" to (b?.voltageV?.let { "%.3f V".format(it) } ?: "--"),
                "STATE" to (b?.status ?: "--"),
            ),
            when {
                hot -> "WARNING: CELL OVERHEAT"
                low -> "WARNING: RESERVE CRITICAL"
                else -> "POWER SYSTEMS NOMINAL"
            },
            hot || low,
        )
    }
}

/** Sensor value (0..1) that drives the live scan sound for each sub-system. */
fun scanIntensity(sub: SubSystem, s: TricorderState): Float = when (sub) {
    SubSystem.GEO -> ((s.magField?.magnitude ?: 40f) - 30f) / 120f
    SubSystem.METEO -> log10((s.lux ?: 10f).coerceAtLeast(1f)) / 5f
    SubSystem.BIO -> (s.linearAccel?.magnitude ?: 0f) / 3f
    SubSystem.ENG -> ((s.battery?.temperatureC ?: 25f) - 25f) / 20f
    SubSystem.TRACKER -> 0f
}.coerceIn(0f, 1f)

fun voiceFor(sub: SubSystem): SoundEngine.Voice = when (sub) {
    SubSystem.GEO -> SoundEngine.Voice.GEO
    SubSystem.METEO -> SoundEngine.Voice.TRICORDER
    SubSystem.BIO -> SoundEngine.Voice.MEDICAL
    SubSystem.ENG -> SoundEngine.Voice.ENGINEERING
    SubSystem.TRACKER -> SoundEngine.Voice.NONE
}

@Composable
fun ReportPanel(report: ScanReport, color: androidx.compose.ui.graphics.Color, onAcknowledge: () -> Unit) {
    val accent = if (report.anomaly) LcarsColors.Red else color
    Column(
        Modifier
            .fillMaxWidth()
            .background(LcarsColors.Black, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            BarSegment(accent, Modifier.padding(end = 6.dp).height(30.dp).fillMaxWidth(0.12f), capStart = true)
            Text(report.title, style = LcarsType.Label.copy(color = accent))
        }
        Text(
            "ANALYSIS COMPLETE",
            style = LcarsType.Small.copy(color = LcarsColors.Tan),
            modifier = Modifier.padding(start = 8.dp),
        )
        Column(Modifier.padding(horizontal = 8.dp)) {
            report.lines.forEach { (k, v) -> Readout(k, v) }
        }
        Text(
            report.verdict,
            style = LcarsType.Label.copy(color = accent),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(accent, RoundedCornerShape(50))
                .clickable(onClick = onAcknowledge),
            contentAlignment = Alignment.Center,
        ) {
            Text("ACKNOWLEDGE", style = LcarsType.Button)
        }
    }
}
