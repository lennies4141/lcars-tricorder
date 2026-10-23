package com.tricorder.lcars.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.tricorder.lcars.audio.Chirper
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.scans.BioScan
import com.tricorder.lcars.ui.scans.EngScan
import com.tricorder.lcars.ui.scans.GeoScan
import com.tricorder.lcars.ui.scans.MeteoScan
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SubSystem(val label: String, val code: String, val color: Color) {
    GEO("GEO-SCAN", "01-4471", LcarsColors.Orange),
    METEO("METEO-SCAN", "02-1138", LcarsColors.Periwinkle),
    BIO("BIO-SCAN", "03-7724", LcarsColors.Lilac),
    ENG("ENG-SCAN", "04-0047", LcarsColors.Yellow),
}

private val SIDEBAR = 112.dp
private val BAR = 26.dp
private val GAP = 5.dp

@Composable
fun TricorderScreen(state: TricorderState, chirper: Chirper) {
    var current by rememberSaveable { mutableStateOf(SubSystem.GEO) }
    var audioOn by rememberSaveable { mutableStateOf(true) }
    chirper.enabled = audioOn
    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .background(LcarsColors.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // ── Top frame ───────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().height(96.dp)) {
            Elbow(
                color = current.color,
                sidebarWidth = SIDEBAR,
                barHeight = BAR,
                bottom = false,
                label = current.code,
                modifier = Modifier.width(SIDEBAR + 70.dp).fillMaxHeight(),
            )
            Spacer(Modifier.width(GAP))
            Column(Modifier.weight(1f)) {
                Row(Modifier.height(BAR), verticalAlignment = Alignment.CenterVertically) {
                    BarSegment(LcarsColors.Tan, Modifier.weight(1f).height(BAR))
                    Spacer(Modifier.width(GAP))
                    BarSegment(LcarsColors.Sunset, Modifier.width(28.dp).height(BAR), capEnd = true)
                }
                Spacer(Modifier.height(6.dp))
                Text("TRICORDER", style = LcarsType.Title, modifier = Modifier.align(Alignment.End))
                Text(
                    current.label,
                    style = LcarsType.Label.copy(color = current.color),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }

        Spacer(Modifier.height(GAP))

        // ── Sidebar + content ───────────────────────────────────────
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.width(SIDEBAR).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                SubSystem.entries.forEach { sub ->
                    SidebarButton(
                        text = sub.label,
                        color = sub.color,
                        selected = sub == current,
                        modifier = Modifier.height(64.dp),
                    ) {
                        if (current != sub) {
                            current = sub
                            chirper.play(Chirper.Sound.TAB)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                BarSegment(LcarsColors.Purple, Modifier.fillMaxWidth().weight(1f))
                SidebarButton(
                    text = if (audioOn) "AUDIO ON" else "AUDIO OFF",
                    color = if (audioOn) LcarsColors.Peach else LcarsColors.Dim,
                    selected = false,
                    modifier = Modifier.height(48.dp),
                ) {
                    audioOn = !audioOn
                    chirper.enabled = audioOn
                    if (audioOn) chirper.play(Chirper.Sound.TOGGLE)
                }
            }

            Spacer(Modifier.width(14.dp))

            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 8 }) togetherWith fadeOut()
                },
                label = "scan",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { sub ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatusLamp("SCANNING // ${sub.label}", sub.color)
                    ScanSweep(sub.color)
                    when (sub) {
                        SubSystem.GEO -> GeoScan(state)
                        SubSystem.METEO -> MeteoScan(state)
                        SubSystem.BIO -> BioScan(state)
                        SubSystem.ENG -> EngScan(state)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(GAP))

        // ── Bottom frame ────────────────────────────────────────────
        BottomFrame(current.color)
    }
}

@Composable
private fun BottomFrame(color: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val dt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val time = dt.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    Row(Modifier.fillMaxWidth().height(72.dp)) {
        Elbow(
            color = color,
            sidebarWidth = SIDEBAR,
            barHeight = BAR,
            bottom = true,
            modifier = Modifier.width(SIDEBAR + 70.dp).fillMaxHeight(),
        )
        Spacer(Modifier.width(GAP))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("SD ${stardate(dt)}  ", style = LcarsType.Small)
                Text(time, style = LcarsType.Small.copy(color = LcarsColors.Yellow))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.height(BAR), verticalAlignment = Alignment.CenterVertically) {
                BarSegment(LcarsColors.Lilac, Modifier.width(40.dp).height(BAR))
                Spacer(Modifier.width(GAP))
                BarSegment(LcarsColors.Orange, Modifier.weight(1f).height(BAR))
                Spacer(Modifier.width(GAP))
                BarSegment(LcarsColors.Ice, Modifier.width(28.dp).height(BAR), capEnd = true)
            }
        }
    }
}

/** TNG-style stardate: 1000 units per year offset from 2323. */
private fun stardate(dt: LocalDateTime): String {
    val daysInYear = if (dt.toLocalDate().isLeapYear) 366.0 else 365.0
    val dayFraction = (dt.dayOfYear - 1 + dt.toLocalTime().toSecondOfDay() / 86400.0) / daysInYear
    val sd = 1000.0 * (dt.year - 2323) + 1000.0 * dayFraction
    return "%.1f".format(sd)
}
