package com.ludzik.ar.ar

import android.annotation.SuppressLint
import android.app.Activity
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.ludzik.ar.audio.Haptic
import com.ludzik.ar.audio.Haptics
import com.ludzik.ar.audio.Sfx
import com.ludzik.ar.audio.SoundManager
import com.ludzik.ar.capture.MediaSaver
import com.ludzik.ar.capture.RecordableConfigChooser
import com.ludzik.ar.characters.CharacterKind
import com.ludzik.ar.characters.World
import com.ludzik.ar.characters.WorldListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ArUiState(
    val hint: TrackingHint = TrackingHint.STARTING,
    val characterCount: Int = 0,
    val recording: Boolean = false,
    val recordSeconds: Int = 0,
    val flashCounter: Int = 0,
    val busySaving: Boolean = false,
)

/**
 * Łączy sesję ARCore, widok GL, renderer, dźwięk i zapis multimediów.
 * Żyje tak długo, jak ekran AR.
 */
class ArController(private val activity: Activity) : WorldListener, ArRendererListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sound = SoundManager(activity)
    private val haptics = Haptics(activity)
    private val world = World().also { it.listener = this }
    private val renderer = ArRenderer(activity, world, this)
    private var session: Session? = null
    private var resumed = false

    private val _state = MutableStateFlow(ArUiState())
    val state: StateFlow<ArUiState> = _state
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    @SuppressLint("ClickableViewAccessibility")
    val glView: GLSurfaceView = GLSurfaceView(activity).apply {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setEGLConfigChooser(RecordableConfigChooser())
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        setWillNotDraw(false)
        val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                renderer.queueTap(e.x, e.y)
                return true
            }
        })
        setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
    }

    /** Tworzy sesję ARCore. Zwraca komunikat błędu albo null przy sukcesie. */
    fun createSession(): String? {
        if (session != null) return null
        return try {
            val s = Session(activity)
            val config = Config(s).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                // Nie czekamy na nową klatkę kamery — animacje mogą chodzić w 60 FPS.
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode = Config.FocusMode.AUTO
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                // Pozwala postawić postać, zanim ARCore znajdzie podłogę (np. jednolite płytki).
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            }
            renderer.instantPlacement = try {
                s.configure(config)
                true
            } catch (e: Exception) {
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                s.configure(config)
                false
            }
            // Zostajemy przy domyślnej konfiguracji kamery: tryb 60 FPS bywa w niższej
            // rozdzielczości, co może utrudniać wykrywanie płaszczyzn. Renderowanie i tak idzie
            // w tempie ekranu dzięki LATEST_CAMERA_IMAGE.
            session = s
            renderer.session = s
            null
        } catch (e: UnavailableArcoreNotInstalledException) {
            "Brakuje Usług Google Play dla AR. Zainstaluj je ze Sklepu Play."
        } catch (e: UnavailableApkTooOldException) {
            "Usługi Google Play dla AR są za stare — zaktualizuj je w Sklepie Play."
        } catch (e: UnavailableSdkTooOldException) {
            "Ta wersja aplikacji jest za stara dla Twoich Usług AR. Zaktualizuj Ludzika AR."
        } catch (e: UnavailableDeviceNotCompatibleException) {
            "Twój telefon nie obsługuje ARCore, więc ludziki nie mogą wyjść z zeszytu."
        } catch (e: Exception) {
            "Nie udało się uruchomić AR: ${e.localizedMessage}"
        }
    }

    fun resume() {
        val s = session ?: return
        if (resumed) return
        try {
            s.resume()
        } catch (e: CameraNotAvailableException) {
            emit("Kamera jest niedostępna. Zamknij inne aplikacje używające aparatu.")
            return
        }
        resumed = true
        renderer.onSessionResumed()
        renderer.rotationHelper.onResume()
        glView.onResume()
    }

    fun pause() {
        if (!resumed) return
        if (_state.value.recording) stopRecording(blocking = true)
        resumed = false
        renderer.rotationHelper.onPause()
        glView.onPause()
        session?.pause()
    }

    fun destroy() {
        pause()
        scope.cancel()
        session?.close()
        session = null
        renderer.session = null
        sound.release()
    }

    // ------------------------------------------------------------------ akcje UI

    fun selectKind(kind: CharacterKind) {
        renderer.selectedKind = kind
        sound.play(Sfx.SCRIBBLE, 0.4f)
    }

    fun clearAll() {
        renderer.clearWorld()
        sound.play(Sfx.SQUEAK)
        haptics.play(Haptic.MEDIUM)
    }

    fun takePhoto() {
        if (_state.value.busySaving) return
        _state.update { it.copy(busySaving = true) }
        renderer.requestPhoto()
    }

    fun toggleRecording() {
        if (_state.value.recording) stopRecording(blocking = false) else startRecording()
    }

    private fun startRecording() {
        renderer.runOnGl {
            val ok = renderer.recorder.start(glView.width, glView.height)
            scope.launch {
                if (!ok) {
                    emit("Nie udało się rozpocząć nagrywania.")
                    return@launch
                }
                _state.update { it.copy(recording = true, recordSeconds = 0) }
                haptics.play(Haptic.LIGHT)
                while (isActive && _state.value.recording) {
                    delay(1000)
                    if (!_state.value.recording) break
                    val sec = _state.value.recordSeconds + 1
                    _state.update { it.copy(recordSeconds = sec) }
                    if (sec >= MAX_RECORD_SECONDS) {
                        emit("Koniec taśmy! Maksymalnie $MAX_RECORD_SECONDS s.")
                        stopRecording(blocking = false)
                    }
                }
            }
        }
    }

    /**
     * Zatrzymanie musi się odbyć na wątku GL (niszczymy powierzchnię EGL).
     * Przy pauzie czekamy na nie, bo po glView.onPause() wątek GL stoi.
     */
    private fun stopRecording(blocking: Boolean) {
        _state.update { it.copy(recording = false, recordSeconds = 0) }
        val latch = CountDownLatch(1)
        val task = {
            val file = renderer.recorder.stop()
            scope.launch(Dispatchers.IO) {
                if (file == null) {
                    emit("Nagranie było za krótkie.")
                } else {
                    val uri = MediaSaver.saveVideo(activity, file)
                    emit(if (uri != null) "Film zapisany w galerii (Filmy/LudzikAR)" else "Nie udało się zapisać filmu.")
                }
            }
            latch.countDown()
        }
        if (blocking) {
            glView.queueEvent(task)
            latch.await(1, TimeUnit.SECONDS)
        } else {
            renderer.runOnGl(task)
        }
        haptics.play(Haptic.DOUBLE)
    }

    // ------------------------------------------------------------------ zdarzenia z renderera (wątek GL)

    override fun onHint(hint: TrackingHint) = _state.update { it.copy(hint = hint) }

    override fun onCharacterCount(count: Int) = _state.update { it.copy(characterCount = count) }

    override fun onMessage(text: String) = emit(text)

    override fun onError(text: String) = emit(text)

    override fun onPhoto(pixels: ByteBuffer, width: Int, height: Int) {
        sound.play(Sfx.SHUTTER)
        haptics.play(Haptic.LIGHT)
        _state.update { it.copy(flashCounter = it.flashCounter + 1) }
        scope.launch(Dispatchers.IO) {
            val bmp = MediaSaver.bitmapFromGl(pixels, width, height)
            val uri = MediaSaver.saveImage(activity, bmp)
            bmp.recycle()
            _state.update { it.copy(busySaving = false) }
            emit(if (uri != null) "Zdjęcie zapisane w galerii (Obrazy/LudzikAR)" else "Nie udało się zapisać zdjęcia.")
        }
    }

    // ------------------------------------------------------------------ WorldListener (wątek GL)

    override fun onSound(sfx: Sfx, volume: Float) = sound.play(sfx, volume)

    override fun onHaptic(haptic: Haptic) = haptics.play(haptic)

    private fun emit(text: String) {
        _messages.tryEmit(text)
    }

    companion object {
        const val MAX_RECORD_SECONDS = 30
    }
}
