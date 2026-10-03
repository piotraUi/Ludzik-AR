package com.ludzik.game

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ludzik.game.audio.Haptic
import com.ludzik.game.audio.Haptics
import com.ludzik.game.audio.Sfx
import com.ludzik.game.audio.SoundManager
import com.ludzik.game.game.GameController
import com.ludzik.game.game.GameEvents
import com.ludzik.game.game.GameHost
import com.ludzik.game.ui.GameScreen
import com.ludzik.game.ui.LudzikTheme
import com.ludzik.game.ui.MediaSaver
import com.ludzik.game.ui.StartScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), GameEvents {

    private var host by mutableStateOf<GameHost?>(null)
    private var toast by mutableStateOf<String?>(null)
    private var flash by mutableIntStateOf(0)
    private var resumed = false

    private lateinit var sound: SoundManager
    private lateinit var haptics: Haptics
    private var pendingStorageAction: (() -> Unit)? = null

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke() else onMessage("Bez zgody na zapis zdjęcie nie trafi do galerii.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        sound = SoundManager(this)
        haptics = Haptics(this)
        setContent {
            LudzikTheme {
                val h = host
                if (h == null) {
                    StartScreen(onStart = ::startGame)
                } else {
                    GameScreen(
                        host = h,
                        toast = toast,
                        flashCounter = flash,
                        onBack = ::closeGame,
                        onPhoto = { withStoragePermission(::takePhoto) },
                        onToastShown = { toast = null },
                    )
                }
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun startGame() {
        if (host != null) return
        val game = GameController().also { it.events = this }
        val h = GameHost(this, game)
        host = h
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (resumed) h.resume()
    }

    private fun closeGame() {
        host?.destroy()
        host = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun takePhoto() {
        val h = host ?: return
        sound.play(Sfx.SHUTTER)
        haptics.play(Haptic.LIGHT)
        flash++
        h.capture { bmp ->
            if (bmp == null) {
                onMessage("Nie udało się zrobić zdjęcia.")
                return@capture
            }
            lifecycleScope.launch {
                val uri = withContext(Dispatchers.IO) { MediaSaver.saveImage(this@MainActivity, bmp).also { bmp.recycle() } }
                onMessage(if (uri != null) "Zdjęcie zapisane w galerii (Obrazy/LudzikAR)" else "Nie udało się zapisać zdjęcia.")
            }
        }
    }

    /** Na Androidzie 8–9 zapis do galerii wymaga WRITE_EXTERNAL_STORAGE. */
    private fun withStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 29 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingStorageAction = action
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // ------------------------------------------------------------------ GameEvents (wątek główny)

    override fun onSound(sfx: Sfx, volume: Float) = sound.play(sfx, volume)

    override fun onHaptic(h: Haptic) = haptics.play(h)

    override fun onMessage(text: String) {
        toast = text
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        hideSystemBars()
        host?.resume()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        host?.pause()
    }

    override fun onDestroy() {
        closeGame()
        sound.release()
        super.onDestroy()
    }
}
