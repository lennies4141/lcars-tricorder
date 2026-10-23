package com.tricorder.lcars

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tricorder.lcars.audio.Chirper
import com.tricorder.lcars.sensors.SensorHub
import com.tricorder.lcars.ui.TricorderScreen

class MainActivity : ComponentActivity() {

    private lateinit var sensorHub: SensorHub
    private val chirper = Chirper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorHub = SensorHub(applicationContext)

        setContent {
            val state by sensorHub.state.collectAsStateWithLifecycle()
            TricorderScreen(state = state, chirper = chirper)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorHub.start()
    }

    override fun onPause() {
        sensorHub.stop()
        super.onPause()
    }

    override fun onDestroy() {
        chirper.release()
        super.onDestroy()
    }
}
