package com.tricorder.lcars.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tricorder.lcars.audio.SoundEngine
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.holo.rememberHoloTime
import com.tricorder.lcars.ui.scans.BioScan
import com.tricorder.lcars.ui.scans.EngScan
import com.tricorder.lcars.ui.scans.GeoScan
import com.tricorder.lcars.ui.scans.MeteoScan
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
private const val SCAN_MS = 6000

@Composable
fun TricorderScreen(state: TricorderState, engine: SoundEngine) {
    var booted by rememberSaveable { mutableStateOf(false) }
    var current by rememberSaveable { mutableStateOf(SubSystem.GEO) }
    var audioOn by rememberSaveable { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var continuous by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestState by rememberUpdatedState(state)
    val latestSub by rememberUpdatedState(current)

    val active = scanning || continuous

    // Feed the synthesizer: voice per sub-system, pitch/speed from live sensors.
    SideEffect {
        engine.enabled = audioOn
        engine.voice = if (active && booted) voiceFor(current) else SoundEngine.Voice.NONE
        engine.intensity = scanIntensity(current, state)
    }

    fun startTimedScan() {
        scanJob?.cancel()
        report = null
        scanJob = scope.launch {
            try {
                scanning = true
                engine.play(SoundEngine.Fx.SCAN_START)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                progress.snapTo(0f)
                progress.animateTo(1f, tween(SCAN_MS, easing = LinearEasing))
                val r = buildReport(latestSub, latestState)
                scanning = false
                report = r
                engine.play(if (r.anomaly) SoundEngine.Fx.ALERT else SoundEngine.Fx.SCAN_COMPLETE)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } finally {
                scanning = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(LcarsColors.Black)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            TopFrame(current)
            Spacer(Modifier.height(GAP))

            // ── Sidebar + content ───────────────────────────────────
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
                            modifier = Modifier.height(60.dp),
                        ) {
                            if (current != sub) {
                                current = sub
                                report = null
                                engine.play(SoundEngine.Fx.TAP)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    BarSegment(LcarsColors.Purple, Modifier.fillMaxWidth().weight(1f))
                    ScanButton(
                        scanning = scanning,
                        continuous = continuous,
                        progress = progress.value,
                        modifier = Modifier.height(110.dp),
                        onTap = {
                            when {
                                continuous -> {
                                    continuous = false
                                    engine.play(SoundEngine.Fx.TOGGLE)
                                }
                                scanning -> {
                                    scanJob?.cancel()
                                    engine.play(SoundEngine.Fx.TOGGLE)
                                }
                                else -> startTimedScan()
                            }
                        },
                        onLongPress = {
                            scanJob?.cancel()
                            report = null
                            continuous = !continuous
                            engine.play(if (continuous) SoundEngine.Fx.SCAN_START else SoundEngine.Fx.TOGGLE)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
                    SidebarButton(
                        text = if (audioOn) "AUDIO ON" else "AUDIO OFF",
                        color = if (audioOn) LcarsColors.Peach else LcarsColors.Dim,
                        selected = false,
                        modifier = Modifier.height(44.dp),
                    ) {
                        audioOn = !audioOn
                        engine.enabled = audioOn
                        if (audioOn) engine.play(SoundEngine.Fx.TOGGLE)
                    }
                }

                Spacer(Modifier.width(14.dp))

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            (fadeIn() + slideInHorizontally { it / 8 }) togetherWith fadeOut()
                        },
                        label = "scan",
                        modifier = Modifier.fillMaxSize(),
                    ) { sub ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatusLamp(
                                when {
                                    scanning -> "SCANNING ${(progress.value * 100).toInt()}%  // ${sub.label}"
                                    continuous -> "CONTINUOUS SCAN // ${sub.label}"
                                    else -> "STANDBY // TAP SCAN"
                                },
                                if (active) sub.color else LcarsColors.Dim,
                            )
                            ScanSweep(sub.color)
                            when (sub) {
                                SubSystem.GEO -> GeoScan(state, active)
                                SubSystem.METEO -> MeteoScan(state, active)
                                SubSystem.BIO -> BioScan(state, active)
                                SubSystem.ENG -> EngScan(state, active)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    if (active) ScanBeamOverlay(current.color, Modifier.matchParentSize())

                    AnimatedVisibility(
                        visible = report != null,
                        enter = fadeIn() + scaleIn(initialScale = 0.92f),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
                    ) {
                        report?.let { r ->
                            ReportPanel(r, current.color) {
                                report = null
                                engine.play(SoundEngine.Fx.TAP)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(GAP))
            BottomFrame(current.color)
        }

        if (!booted) {
            BootSequence(engine) { booted = true }
        }
    }
}

@Composable
private fun TopFrame(current: SubSystem) {
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
}

/** Big sidebar SCAN control. Tap = timed scan, long-press = continuous scan. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanButton(
    scanning: Boolean,
    continuous: Boolean,
    progress: Float,
    modifier: Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "scanBtn")
    val glow by pulse.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "scanGlow",
    )
    val active = scanning || continuous
    val base = LcarsColors.Sunset
    Box(
        modifier
            .fillMaxWidth()
            .background(if (active) base.copy(alpha = glow) else base)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            ),
    ) {
        if (scanning) {
            // Progress fill rising from the bottom
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxHeight(progress.coerceIn(0f, 1f))
                    .background(LcarsColors.Yellow.copy(alpha = 0.55f)),
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                when {
                    continuous -> "LOCKED"
                    scanning -> "ABORT"
                    else -> "HOLD: LOCK"
                },
                style = LcarsType.Button.copy(fontSize = 11.sp),
            )
            Text("SCAN", style = LcarsType.Button.copy(fontSize = 30.sp))
        }
    }
}

/** Translucent scan beam sweeping down over the content while scanning. */
@Composable
private fun ScanBeamOverlay(color: Color, modifier: Modifier) {
    val time = rememberHoloTime()
    Canvas(modifier) {
        val t = time.value
        val y = (t * 0.55f % 1f) * size.height
        val band = size.height * 0.12f
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.22f), Color.Transparent),
                startY = y - band, endY = y + band,
            ),
            topLeft = Offset(0f, y - band),
            size = Size(size.width, band * 2),
        )
        drawLine(color.copy(alpha = 0.8f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
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
