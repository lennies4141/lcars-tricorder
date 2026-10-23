package com.tricorder.lcars

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tricorder.lcars.audio.SoundEngine
import com.tricorder.lcars.sensors.SensorHub
import com.tricorder.lcars.ui.TricorderScreen

class MainActivity : ComponentActivity() {

    private lateinit var sensorHub: SensorHub
    private lateinit var engine: SoundEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorHub = SensorHub(applicationContext)
        engine = SoundEngine()

        setContent {
            val state by sensorHub.state.collectAsStateWithLifecycle()
            TricorderScreen(state = state, engine = engine)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorHub.start()
        engine.start()
    }

    override fun onPause() {
        engine.stop()
        sensorHub.stop()
        super.onPause()
    }
}
