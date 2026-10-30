package io.github.conor2803.coach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import io.github.conor2803.coach.ui.CoachApp
import io.github.conor2803.coach.ui.theme.CoachTheme

class MainActivity : ComponentActivity() {
    /** Bumped every time the app comes to the front, so permission states refresh. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CoachTheme { CoachApp(resumeTick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}
