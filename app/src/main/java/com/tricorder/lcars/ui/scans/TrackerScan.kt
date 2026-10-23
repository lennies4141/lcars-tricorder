package com.tricorder.lcars.ui.scans

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp
import com.tricorder.lcars.audio.SoundEngine
import com.tricorder.lcars.sensors.TricorderState
import com.tricorder.lcars.ui.Readout
import com.tricorder.lcars.ui.SectionHeader
import com.tricorder.lcars.ui.holo.holoBrackets
import com.tricorder.lcars.ui.holo.holoScanlines
import com.tricorder.lcars.ui.holo.holoText
import com.tricorder.lcars.ui.theme.LcarsColors
import com.tricorder.lcars.ui.theme.LcarsType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulated motion-tracker contacts. Contacts live at real compass bearings, so the
 * operator has to physically turn to bring them into the forward detection fan.
 * Phones have no sensor that can see people; this is a deliberate illusion.
 */
class TrackerSim {

    class Contact(
        var bearing: Float,          // world bearing, degrees
        var range: Float,            // meters
        var speed: Float,            // m/s
        var pause: Float = 0f,       // seconds remaining stopped
        var litBearing: Float = 0f,  // snapshot when the pulse last touched it
        var litRange: Float = 0f,
        var litAt: Float = -100f,
        var litPulse: Int = -1,
    )

    class Events(val pulse: Boolean, val returns: List<Float>, val reached: Boolean)

    val contacts = mutableListOf<Contact>()
    var time = 0f
        private set
    var pulsePhase = 0f
        private set
    var pulseCount = 0
        private set
    var alertUntil = -1f
        private set
    var lastNearest: Float? = null
        private set

    private val rng = Random(System.nanoTime())
    private var respawnAt = mutableListOf<Float>()
    private var maxContacts = 2
    private var heading = 0f

    fun reset(heading: Float) {
        this.heading = heading
        contacts.clear()
        respawnAt.clear()
        maxContacts = 2
        repeat(2) { spawn() }
    }

    /** Forces an immediate pulse (tap the display). */
    fun pingNow() {
        pulsePhase = 0.999f
    }

    private fun spawn() {
        // Mostly in front of the operator so they show up; sometimes behind for a turn-around scare.
        val rel = if (rng.nextFloat() < 0.75f) rng.nextFloat() * 110f - 55f else rng.nextFloat() * 360f - 180f
        contacts.add(
            Contact(
                bearing = heading + rel,
                range = MAX_RANGE * (0.75f + rng.nextFloat() * 0.25f),
                speed = 0.6f + rng.nextFloat() * 1.1f,
            )
        )
    }

    fun nearest(): Contact? = contacts.minByOrNull { it.range }

    /** Pulse cadence speeds up as the nearest contact closes in. */
    fun cycleSeconds(): Float =
        nearest()?.let { 0.42f + 1.0f * (it.range / MAX_RANGE).coerceIn(0f, 1f) } ?: 1.5f

    fun update(dt: Float, heading: Float): Events {
        this.heading = heading
        time += dt
        var reached = false

        // Contact movement: stalk, pause, drift sideways
        val it = contacts.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c.pause > 0f) {
                c.pause -= dt
            } else {
                c.range -= c.speed * dt
                c.bearing += (rng.nextFloat() - 0.5f) * 14f * dt
                if (rng.nextFloat() < 0.18f * dt) c.pause = 0.5f + rng.nextFloat() * 1.8f
            }
            if (c.range <= 1.2f) {
                reached = true
                alertUntil = time + 1.6f
                it.remove()
                respawnAt.add(time + 2f + rng.nextFloat() * 3f)
            }
        }
        // Respawns and escalation ("they keep coming")
        val due = respawnAt.filter { it <= time }
        if (due.isNotEmpty()) {
            respawnAt.removeAll(due)
            due.forEach { if (contacts.size < maxContacts) spawn() }
        }
        if (maxContacts < 5 && time > 25f * (maxContacts - 1)) {
            maxContacts++
            spawn()
        }

        // Pulse wavefront
        var pulse = false
        pulsePhase += dt / cycleSeconds()
        if (pulsePhase >= 1f) {
            pulsePhase -= 1f
            pulseCount++
            pulse = true
        }
        val front = pulsePhase * MAX_RANGE
        val returns = ArrayList<Float>()
        for (c in contacts) {
            if (c.litPulse != pulseCount && front >= c.range) {
                c.litPulse = pulseCount
                c.litBearing = c.bearing
                c.litRange = c.range
                c.litAt = time
                if (inFan(c.bearing)) returns.add(c.range)
            }
        }
        lastNearest = nearest()?.range
        return Events(pulse, returns, reached)
    }

    fun relBearing(world: Float): Float {
        var d = (world - heading) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun inFan(world: Float) = abs(relBearing(world)) <= FAN_HALF

    companion object {
        const val MAX_RANGE = 25f
        const val FAN_HALF = 60f
    }
}

@Composable
fun TrackerScan(
    s: TricorderState,
    sim: TrackerSim,
    engine: SoundEngine,
    onHoloTap: () -> Unit,
) {
    val heading by rememberUpdatedState(s.headingDeg ?: 0f)
    val haptics = LocalHapticFeedback.current
    val tick = remember { mutableIntStateOf(0) }

    LaunchedEffect(sim) {
        if (sim.contacts.isEmpty()) sim.reset(heading)
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                val ev = sim.update(dt, heading)
                if (ev.pulse) {
                    // Outgoing pulse: soft falling tone
                    engine.beep(620.0, 0.09, amp = 0.26, glideTo = 540.0)
                }
                for (r in ev.returns) {
                    // Return: pitch rises as the contact closes
                    val closeness = 1.0 - (r / TrackerSim.MAX_RANGE).coerceIn(0f, 1f)
                    engine.beep(700.0 + 800.0 * closeness, 0.13, amp = 0.22 + 0.15 * closeness)
                    if (r < 8f) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (ev.reached) {
                    engine.play(SoundEngine.Fx.ALERT)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                tick.intValue++
            }
        }
    }

    // Reading tick keeps the readouts live every frame
    tick.intValue
    val nearest = sim.lastNearest
    val n = sim.nearest()
    val alert = sim.time < sim.alertUntil

    SectionHeader("MOTION TRACKER", LcarsColors.Mint)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            nearest?.let { "%02d".format(it.toInt()) } ?: "--",
            style = LcarsType.BigReadout.copy(
                fontSize = 56.sp,
                color = if (alert) LcarsColors.Red else LcarsColors.Mint,
            ),
        )
        Text(" m", style = LcarsType.Title.copy(color = LcarsColors.Mint))
        Spacer(Modifier.weight(1f))
        Text(
            if (alert) "CONTACT" else "${sim.contacts.size} SIGNAL${if (sim.contacts.size == 1) "" else "S"}",
            style = LcarsType.Title.copy(color = if (alert) LcarsColors.Red else LcarsColors.Tan),
        )
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .pointerInput(Unit) {
                detectTapGestures {
                    sim.pingNow()
                    onHoloTap()
                }
            }
    ) {
        tick.intValue // invalidate every frame
        val mint = LcarsColors.Mint
        val origin = Offset(size.width / 2f, size.height * 0.97f)
        val radius = size.height * 0.9f
        val fanStart = 270f - TrackerSim.FAN_HALF
        val fanSweep = TrackerSim.FAN_HALF * 2

        fun polar(relDeg: Float, r: Float): Offset {
            val a = Math.toRadians(relDeg.toDouble())
            return Offset(origin.x + (sin(a) * r).toFloat(), origin.y - (cos(a) * r).toFloat())
        }

        // Fan background
        drawArc(
            Brush.radialGradient(
                listOf(mint.copy(alpha = 0.10f), mint.copy(alpha = 0.02f)),
                center = origin, radius = radius,
            ),
            fanStart, fanSweep, useCenter = true,
            topLeft = Offset(origin.x - radius, origin.y - radius), size = Size(radius * 2, radius * 2),
        )
        // Range rings every 5 m
        for (m in 5..TrackerSim.MAX_RANGE.toInt() step 5) {
            val r = radius * m / TrackerSim.MAX_RANGE
            drawArc(
                mint.copy(alpha = 0.35f), fanStart, fanSweep, useCenter = false,
                topLeft = Offset(origin.x - r, origin.y - r), size = Size(r * 2, r * 2),
                style = Stroke(1.5f),
            )
            holoText("$m", polar(TrackerSim.FAN_HALF + 4f, r), mint.copy(alpha = 0.7f), size.minDimension * 0.035f)
        }
        // Radials every 15°
        var d = -TrackerSim.FAN_HALF
        while (d <= TrackerSim.FAN_HALF) {
            val edge = abs(d) == TrackerSim.FAN_HALF
            drawLine(mint.copy(alpha = if (edge) 0.7f else 0.18f), origin, polar(d, radius), strokeWidth = if (edge) 2.5f else 1f)
            d += 15f
        }

        // Pulse wavefront with a fading trail
        val front = radius * sim.pulsePhase
        if (front > 2f) {
            drawArc(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.75f to mint.copy(alpha = 0.05f),
                    1f to mint.copy(alpha = 0.28f),
                    center = origin, radius = front,
                ),
                fanStart, fanSweep, useCenter = true,
                topLeft = Offset(origin.x - front, origin.y - front), size = Size(front * 2, front * 2),
            )
            drawArc(
                mint.copy(alpha = 0.9f), fanStart, fanSweep, useCenter = false,
                topLeft = Offset(origin.x - front, origin.y - front), size = Size(front * 2, front * 2),
                style = Stroke(3f),
            )
        }

        // Blips: drawn where the pulse last found them, fading until the next pulse
        val fade = sim.cycleSeconds() * 1.15f
        for (c in sim.contacts) {
            val age = sim.time - c.litAt
            if (age < 0f || age > fade) continue
            val rel = sim.relBearing(c.litBearing)
            if (abs(rel) > TrackerSim.FAN_HALF + 2f) continue
            val p = polar(rel, radius * c.litRange / TrackerSim.MAX_RANGE)
            val a = 1f - age / fade
            val blob = size.minDimension * 0.045f
            drawCircle(mint.copy(alpha = a * 0.18f), blob * 2.2f, p)
            drawCircle(mint.copy(alpha = a * 0.45f), blob * 1.3f, p)
            drawCircle(Color.White.copy(alpha = a * 0.9f), blob * 0.6f, p)
        }

        // Off-fan guidance chevrons: which way to turn
        n?.let { c ->
            val rel = sim.relBearing(c.bearing)
            if (abs(rel) > TrackerSim.FAN_HALF) {
                val left = rel < 0
                val y = size.height * 0.35f
                val x = if (left) size.width * 0.06f else size.width * 0.94f
                val dir = if (left) -1f else 1f
                val blink = ((sim.time * 3f) % 1f) < 0.6f
                if (blink) {
                    val chev = Path().apply {
                        moveTo(x - dir * 18f, y - 26f)
                        lineTo(x, y)
                        lineTo(x - dir * 18f, y + 26f)
                    }
                    drawPath(chev, LcarsColors.Sunset, style = Stroke(5f))
                    holoText(
                        "TURN", Offset(x - dir * 10f, y + 48f),
                        LcarsColors.Sunset, size.minDimension * 0.035f,
                    )
                }
            }
        }

        // Proximity alert flash
        if (alert && ((sim.time * 6f) % 1f) < 0.5f) {
            drawRect(LcarsColors.Red.copy(alpha = 0.18f))
            holoText("CONTACT", Offset(size.width / 2f, size.height * 0.4f), LcarsColors.Red, size.minDimension * 0.11f)
        }

        drawCircle(mint, 6f, origin)
        holoScanlines(mint, sim.time)
        holoBrackets(mint)
        holoText("SIM", Offset(size.width * 0.06f, size.height * 0.05f), mint.copy(alpha = 0.5f), size.minDimension * 0.03f)
    }

    Readout("NEAREST", nearest?.let { "%.1f".format(it) }, "m", LcarsColors.Mint)
    Readout("BEARING", n?.let { "%+.0f".format(sim.relBearing(it.bearing)) }, "°")
    Readout("CLOSURE", n?.let { if (it.pause > 0f) "HOLDING" else "%.1f m/s".format(it.speed) })
    Readout("PULSE", "%.2f".format(sim.cycleSeconds()), "s")
    Text("TAP DISPLAY: FORCE PULSE", style = LcarsType.Small)
}
