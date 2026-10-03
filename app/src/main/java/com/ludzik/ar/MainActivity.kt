package com.ludzik.ar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.ludzik.ar.ar.ArController
import com.ludzik.ar.ui.ArScreen
import com.ludzik.ar.ui.CheckingScreen
import com.ludzik.ar.ui.LudzikTheme
import com.ludzik.ar.ui.PermissionScreen
import com.ludzik.ar.ui.StartScreen
import com.ludzik.ar.ui.UnsupportedScreen

sealed interface Screen {
    data object Start : Screen
    data object Checking : Screen
    data class Permission(val permanentlyDenied: Boolean) : Screen
    data class Unsupported(val message: String) : Screen
    data object Ar : Screen
}

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen>(Screen.Start)
    private var controller by mutableStateOf<ArController?>(null)

    /** Czekamy na powrót ze Sklepu Play po instalacji ARCore. */
    private var installRequested = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStorageAction: (() -> Unit)? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            checkArCore()
        } else {
            val canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            screen = Screen.Permission(permanentlyDenied = !canAskAgain)
        }
    }

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStorageAction
        pendingStorageAction = null
        if (granted) action?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LudzikTheme {
                when (val s = screen) {
                    Screen.Start -> StartScreen(onStart = ::startFlow)
                    Screen.Checking -> CheckingScreen()
                    is Screen.Permission -> PermissionScreen(
                        permanentlyDenied = s.permanentlyDenied,
                        onRequest = { cameraPermission.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = ::openAppSettings,
                        onBack = { screen = Screen.Start },
                    )
                    is Screen.Unsupported -> UnsupportedScreen(s.message, onBack = { screen = Screen.Start })
                    Screen.Ar -> controller?.let { c ->
                        ArScreen(
                            controller = c,
                            onBack = ::closeAr,
                            onPhoto = { withStoragePermission { c.takePhoto() } },
                            onRecord = { withStoragePermission { c.toggleRecording() } },
                        )
                    }
                }
            }
        }
    }

    private fun startFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            checkArCore()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /** Sprawdza ARCore; w razie potrzeby prosi o instalację z Google Play. */
    private fun checkArCore() {
        screen = Screen.Checking
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            handler.postDelayed(::checkArCore, 200)
            return
        }
        when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> openAr()
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
            -> try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> installRequested = true
                    ArCoreApk.InstallStatus.INSTALLED -> openAr()
                    else -> openAr()
                }
            } catch (e: UnavailableUserDeclinedInstallationException) {
                installRequested = false
                screen = Screen.Unsupported("Bez Usług Google Play dla AR ludziki nie wyjdą z zeszytu. Zainstaluj je i spróbuj ponownie.")
            } catch (e: Exception) {
                installRequested = false
                screen = Screen.Unsupported("Nie udało się zainstalować ARCore: ${e.localizedMessage}")
            }
            else -> screen = Screen.Unsupported(
                "Ten telefon nie obsługuje ARCore (Usług Google Play dla AR), więc ludziki nie mogą chodzić po Twoim pokoju. " +
                    "Listę wspieranych urządzeń znajdziesz na developers.google.com/ar/devices.",
            )
        }
    }

    private fun openAr() {
        installRequested = false
        val c = controller ?: ArController(this).also { controller = it }
        val error = c.createSession()
        if (error != null) {
            c.destroy()
            controller = null
            screen = Screen.Unsupported(error)
            return
        }
        screen = Screen.Ar
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        c.resume()
    }

    private fun closeAr() {
        controller?.destroy()
        controller = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screen = Screen.Start
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

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onResume() {
        super.onResume()
        when {
            installRequested -> checkArCore()
            screen is Screen.Permission &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> checkArCore()
            screen == Screen.Ar -> controller?.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        controller?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        controller?.destroy()
        controller = null
        super.onDestroy()
    }
}
