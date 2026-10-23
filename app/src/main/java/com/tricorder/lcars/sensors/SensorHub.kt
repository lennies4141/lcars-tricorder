package com.tricorder.lcars.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

data class BatteryInfo(
    val levelPercent: Int,
    val temperatureC: Float,
    val voltageV: Float,
    val status: String,
    val health: String,
    val powerSource: String,
    val technology: String,
)

/** Which sensors exist on this device (null reading + false here = OFFLINE). */
data class SensorAvailability(
    val light: Boolean = false,
    val accelerometer: Boolean = false,
    val gyroscope: Boolean = false,
    val magnetometer: Boolean = false,
    val barometer: Boolean = false,
    val proximity: Boolean = false,
    val linearAcceleration: Boolean = false,
    val rotationVector: Boolean = false,
)

data class TricorderState(
    val available: SensorAvailability = SensorAvailability(),
    val lux: Float? = null,
    val accel: Vec3? = null,
    val gyro: Vec3? = null,
    val magField: Vec3? = null,
    val linearAccel: Vec3? = null,
    /** Rolling history of linear-acceleration magnitude for the BIO-SCAN waveform. */
    val motionHistory: List<Float> = emptyList(),
    val pressureHpa: Float? = null,
    val altitudeM: Float? = null,
    val proximityCm: Float? = null,
    val proximityMaxCm: Float? = null,
    /** Compass heading, degrees 0..360 (magnetic north). */
    val headingDeg: Float? = null,
    val pitchDeg: Float? = null,
    val rollDeg: Float? = null,
    /** Row-major 3x3 device→world rotation matrix (world: X east, Y north, Z up). */
    val rotation: FloatArray? = null,
    val battery: BatteryInfo? = null,
)

/**
 * Owns every SensorManager registration plus the battery receiver and publishes
 * one immutable [TricorderState] snapshot for the Compose UI to observe.
 * Call [start] in onResume and [stop] in onPause.
 */
class SensorHub(private val context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _state = MutableStateFlow(
        TricorderState(
            available = SensorAvailability(
                light = light != null,
                accelerometer = accelerometer != null,
                gyroscope = gyroscope != null,
                magnetometer = magnetometer != null,
                barometer = barometer != null,
                proximity = proximity != null,
                linearAcceleration = linearAccel != null,
                rotationVector = rotationVector != null,
            ),
            proximityMaxCm = proximity?.maximumRange,
        )
    )
    val state: StateFlow<TricorderState> = _state.asStateFlow()

    // Fallback compass math (used only if no rotation-vector sensor exists).
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveGravity = false
    private var haveGeomagnetic = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            _state.update { it.copy(battery = parseBattery(intent)) }
        }
    }

    fun start() {
        val ui = SensorManager.SENSOR_DELAY_UI
        val game = SensorManager.SENSOR_DELAY_GAME
        light?.let { sensorManager.registerListener(this, it, ui) }
        accelerometer?.let { sensorManager.registerListener(this, it, ui) }
        gyroscope?.let { sensorManager.registerListener(this, it, ui) }
        magnetometer?.let { sensorManager.registerListener(this, it, ui) }
        barometer?.let { sensorManager.registerListener(this, it, ui) }
        proximity?.let { sensorManager.registerListener(this, it, ui) }
        linearAccel?.let { sensorManager.registerListener(this, it, game) }
        rotationVector?.let { sensorManager.registerListener(this, it, ui) }

        // Battery status is a sticky system broadcast: the register call returns
        // the latest value immediately, then the receiver fires on each change.
        val sticky = ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { intent -> _state.update { it.copy(battery = parseBattery(intent)) } }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        runCatching { context.unregisterReceiver(batteryReceiver) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> _state.update { it.copy(lux = v[0]) }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(v, 0, gravity, 0, 3)
                haveGravity = true
                _state.update { it.copy(accel = Vec3(v[0], v[1], v[2])) }
                if (rotationVector == null) updateFallbackOrientation()
            }

            Sensor.TYPE_GYROSCOPE -> _state.update { it.copy(gyro = Vec3(v[0], v[1], v[2])) }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(v, 0, geomagnetic, 0, 3)
                haveGeomagnetic = true
                _state.update { it.copy(magField = Vec3(v[0], v[1], v[2])) }
                if (rotationVector == null) updateFallbackOrientation()
            }

            Sensor.TYPE_PRESSURE -> {
                val hPa = v[0]
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa)
                _state.update { it.copy(pressureHpa = hPa, altitudeM = alt) }
            }

            Sensor.TYPE_PROXIMITY -> _state.update { it.copy(proximityCm = v[0]) }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val vec = Vec3(v[0], v[1], v[2])
                _state.update {
                    val history = (it.motionHistory + vec.magnitude).takeLast(HISTORY_SIZE)
                    it.copy(linearAccel = vec, motionHistory = history)
                }
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, v)
                publishOrientation()
            }
        }
    }

    private fun updateFallbackOrientation() {
        if (!haveGravity || !haveGeomagnetic) return
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
            publishOrientation()
        }
    }

    private fun publishOrientation() {
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
        val rot = rotationMatrix.copyOf()
        _state.update { it.copy(headingDeg = azimuth, pitchDeg = pitch, rollDeg = roll, rotation = rot) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun parseBattery(intent: Intent): BatteryInfo {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT CHARGING"
            else -> "UNKNOWN"
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "NOMINAL"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            else -> "UNKNOWN"
        }
        val source = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC MAINS"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "INDUCTIVE"
            else -> "INTERNAL CELL"
        }
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.uppercase() ?: "UNKNOWN"

        return BatteryInfo(
            levelPercent = if (level >= 0) level * 100 / scale else -1,
            temperatureC = tempTenths / 10f,
            voltageV = voltageMv / 1000f,
            status = status,
            health = health,
            powerSource = source,
            technology = tech,
        )
    }

    companion object {
        const val HISTORY_SIZE = 150
    }
}
