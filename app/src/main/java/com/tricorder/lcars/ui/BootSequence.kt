package com.tricorder.lcars.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tricorder.lcars.audio.SoundEngine
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType
import kotlinx.coroutines.delay

private val BOOT_LOG = listOf(
    "SUBSPACE SENSOR ARRAY ........ ONLINE",
    "GEOMAGNETIC FLUXGATE ......... ONLINE",
    "INERTIAL GYRO PLATFORM ....... ONLINE",
    "ATMOSPHERIC ANALYZER ......... ONLINE",
    "PHOTOMETRIC RECEPTORS ........ ONLINE",
    "KINETIC BIO-MONITOR .......... ONLINE",
    "POWER CELL TELEMETRY ......... ONLINE",
    "LCARS INTERFACE .............. READY",
)

/** Power-up sequence shown on launch. Tap anywhere to skip. */
@Composable
fun BootSequence(engine: SoundEngine, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(150)
        engine.play(SoundEngine.Fx.BOOT)
        p.animateTo(1f, tween(2700, easing = LinearEasing))
        delay(350)
        onDone()
    }
    val v = p.value
    Box(
        Modifier
            .fillMaxSize()
            .background(LcarsColors.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone,
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Bars growing in from the left
            val bars = listOf(LcarsColors.Orange, LcarsColors.Lilac, LcarsColors.Periwinkle, LcarsColors.Yellow)
            bars.forEachIndexed { i, c ->
                val f = ((v * 1.6f) - i * 0.12f).coerceIn(0f, 1f)
                if (f > 0f) {
                    BarSegment(c, Modifier.fillMaxWidth(f).height(14.dp), capEnd = true)
                } else {
                    Spacer(Modifier.height(14.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("LCARS", style = LcarsType.Title.copy(fontSize = 64.sp, color = LcarsColors.Orange.copy(alpha = (v * 3f).coerceIn(0f, 1f))))
            Text("TRICORDER // MARK VII", style = LcarsType.Label.copy(color = LcarsColors.Tan.copy(alpha = ((v - 0.15f) * 4f).coerceIn(0f, 1f))))
            Spacer(Modifier.height(12.dp))
            val shown = (v * (BOOT_LOG.size + 1)).toInt().coerceAtMost(BOOT_LOG.size)
            BOOT_LOG.take(shown).forEachIndexed { i, line ->
                Text(
                    line,
                    style = LcarsType.Small.copy(
                        color = if (i == BOOT_LOG.lastIndex) LcarsColors.Yellow else LcarsColors.Ice,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            SegmentGauge(v, LcarsColors.Orange)
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text("INITIALIZING %3d%%".format((v * 100).toInt()), style = LcarsType.Small.copy(color = LcarsColors.Orange))
            }
        }
    }
}
