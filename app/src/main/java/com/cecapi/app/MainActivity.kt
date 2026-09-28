package com.cecapi.app

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.cecapi.app.core.navigation.CecapiNavGraph
import com.cecapi.app.core.theme.CecapiTheme
import com.cecapi.app.core.ui.LocalVoiceHelp
import com.cecapi.app.core.ui.VoiceHelp
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.GlobalVoiceCommands
import com.cecapi.app.core.voice.LaunchRequests
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.WakeWordController
import androidx.lifecycle.lifecycleScope
import com.cecapi.app.service.BackgroundListening
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var voiceEngine: VoiceEngine
    @Inject lateinit var wakeWordController: WakeWordController
    @Inject lateinit var volumeControl: VolumeControl

    // Injecting it is what makes the saved voice speed and cue settings apply at startup.
    @Inject lateinit var deviceSettings: DeviceSettings
    @Inject lateinit var launchRequests: LaunchRequests

    // Injecting these is what registers the global voice commands and the optional background listening.
    @Inject lateinit var globalVoiceCommands: GlobalVoiceCommands
    @Inject lateinit var backgroundListening: BackgroundListening

    // The widget's "Hablar" button. Cold start: the first screen answers instead of its long greeting.
    // Already open: answer right now.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent, alreadyRunning = true)
    }

    private fun handleLaunchIntent(intent: Intent?, alreadyRunning: Boolean) {
        if (intent?.action != ACTION_LISTEN) return
        intent.action = null // handled: a recreated activity must not repeat it
        if (alreadyRunning) {
            voiceEngine.speak(voiceEngine.wakePrompt(), listenAfter = true)
        } else {
            launchRequests.requestListen()
        }
    }

    // The volume buttons change the level and the assistant says the new one out loud.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeControl.onVolumeKey()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        wakeWordController.setForeground(true)
        voiceEngine.appVisible = true
        voiceEngine.activate() // opening the app ends any "silencio" or "para"
        lifecycleScope.launch { backgroundListening.syncWithSetting() }
    }

    // The app left the screen (home button, screen lock, another app): stop talking and listening
    // so the phone is fully usable again. Everything resumes when the user opens CECAPI.
    override fun onStop() {
        wakeWordController.setForeground(false)
        voiceEngine.appVisible = false
        voiceEngine.stopSpeaking()
        voiceEngine.stopListening()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent, alreadyRunning = false)
        enableEdgeToEdge()
        // While the app is open the buttons always control media volume (what the assistant speaks with).
        volumeControlStream = AudioManager.STREAM_MUSIC
        // A blind user cannot tell whether the screen went dark, so keep it on while the app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CecapiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Edge-to-edge draws under the status/navigation bars; keep content clear of them.
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        val navController = rememberNavController()
                        val voiceHelp = remember { VoiceHelp(voiceEngine) }
                        CompositionLocalProvider(LocalVoiceHelp provides voiceHelp) {
                            CecapiNavGraph(navController = navController)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Sent by the home-screen widget: open the assistant and start listening. */
        const val ACTION_LISTEN = "com.cecapi.app.action.LISTEN"
    }
}
