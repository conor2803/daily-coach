package io.github.conor2803.coach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown by Health Connect when you tap "privacy policy" on the permissions screen. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.statusBarsPadding().padding(24.dp)) {
                        Text("How Coach uses your health data", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Coach reads sleep, heart rate, HRV, resting heart rate, steps, blood oxygen, " +
                                "distance and workouts from Health Connect. It never writes to Health Connect.\n\n" +
                                "A few times a day it builds a short text summary on your phone and sends it, " +
                                "together with your calendar, weather and the places you've visited, to " +
                                "Anthropic's Claude API to get your plan. Nothing else leaves the phone and " +
                                "nothing is sent to any other server.",
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
