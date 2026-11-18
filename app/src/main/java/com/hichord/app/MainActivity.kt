package com.hichord.app
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import android.view.*
import android.view.InputDevice
import android.widget.Toast

class MainActivity : Activity() {
    private val synth = Synth()
    private lateinit var view: HiChordView
    private val prefs by lazy { getSharedPreferences("hichord", MODE_PRIVATE) }
    private var upd: Updater.Info? = null
    private var updBusy = false
    // avisa quando o usuario liga/desliga a rotacao automatica do celular
    private val rotObs = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { applyRotation() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // tela cheia, usando o espaco ao redor do furo da camera (o app respeita a area segura)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        prefs.edit().remove("theme_uri").apply()   // tema removido: limpa o que ficou salvo
        view = HiChordView(this, synth)
        view.onUpdate = { onUpdatePressed() }
        setContentView(view)
        hideBars()
        applyRotation()
        checkUpdate(true)
    }
    override fun onStart() {
        super.onStart(); synth.start(); view.midiStart()
        applyRotation()
        try {
            contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, rotObs)
        } catch (e: Exception) {}
    }
    override fun onStop() {
        try { contentResolver.unregisterContentObserver(rotObs) } catch (e: Exception) {}
        view.midiStop(); synth.allOff(); synth.stop(); super.onStop()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!view.closeHelp()) super.onBackPressed()
    }

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Sempre deitado. So gira para vertical se a rotacao automatica do celular estiver LIGADA.
    private fun autoRotateOn(): Boolean = try {
        Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    } catch (e: Exception) { false }
    private fun applyRotation() {
        requestedOrientation = if (autoRotateOn()) ActivityInfo.SCREEN_ORIENTATION_SENSOR
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // ---------- atualizacao pelo app ----------
    @Suppress("DEPRECATION")
    private fun currentVersion(): Long {
        return try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        } catch (e: Exception) { 0L }
    }
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }

    private fun onUpdatePressed() {
        if (updBusy) return
        val u = upd
        if (u != null) startDownload(u) else checkUpdate(false)
    }
    /** silent=true: so avisa no botao (ao abrir o app). silent=false: verifica e ja baixa. */
    private fun checkUpdate(silent: Boolean) {
        if (updBusy) return
        updBusy = true
        if (!silent) toast("Verificando atualização...")
        Thread {
            var found: Updater.Info? = null
            var failed = false
            try { found = Updater.fetchLatest() } catch (e: Exception) { failed = true }
            val r = found; val bad = failed
            runOnUiThread {
                updBusy = false
                if (r != null && r.version > currentVersion()) {
                    upd = r
                    view.setUpdate("Atualizar ●", true)
                    if (!silent) startDownload(r)
                } else {
                    upd = null
                    view.setUpdate("Atualizar", false)
                    if (!silent) toast(if (bad) "Não consegui verificar. Veja a internet (e se o repositório é público)."
                    else "Você já está na versão mais recente (v" + currentVersion() + ")")
                }
            }
        }.start()
    }
    private fun startDownload(info: Updater.Info) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            toast("Permita \"instalar apps desta fonte\" para o HiChord e toque em Atualizar de novo")
            try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
            return
        }
        updBusy = true
        view.setUpdate("Baixando 0%", true)
        Thread {
            try {
                val f = Updater.download(this, info) { pct -> runOnUiThread { view.setUpdate("Baixando $pct%", true) } }
                runOnUiThread { updBusy = false; view.setUpdate("Atualizar ●", true); install(f) }
            } catch (e: Exception) {
                runOnUiThread { updBusy = false; view.setUpdate("Atualizar ●", true); toast("Falha no download. Toque em Atualizar para tentar de novo.") }
            }
        }.start()
    }
    private fun install(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (e: Exception) { toast("Não consegui abrir o instalador.") }
    }

    // ---------- controle ----------
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val s = e.source
        if ((s and InputDevice.SOURCE_GAMEPAD) != 0 || (s and InputDevice.SOURCE_DPAD) != 0) {
            if (view.onPadKey(e.keyCode, e.action == KeyEvent.ACTION_DOWN, e.repeatCount)) return true
        }
        return super.dispatchKeyEvent(e)
    }
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if ((e.source and InputDevice.SOURCE_JOYSTICK) != 0 && e.action == MotionEvent.ACTION_MOVE) {
            view.onPadMotion(e); return true
        }
        return super.onGenericMotionEvent(e)
    }
}
