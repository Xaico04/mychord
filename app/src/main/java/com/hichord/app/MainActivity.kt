package com.hichord.app
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.InputDevice
import android.widget.Toast
import kotlin.math.max

class MainActivity : Activity() {
    private val synth = Synth()
    private lateinit var view: HiChordView
    private lateinit var orient: OrientationEventListener
    private val prefs by lazy { getSharedPreferences("hichord", MODE_PRIVATE) }
    private val reqTheme = 42
    // abre sempre deitado; depois que o celular for girado de verdade, acompanha o sensor
    private var wantLandscape = true
    private var armed = true

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = HiChordView(this, synth)
        view.onTheme = { pickTheme() }
        view.onResetTheme = { prefs.edit().remove("theme_uri").apply(); view.applyTheme(null) }
        view.onRotate = { toggleRotate() }
        setContentView(view)
        orient = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (!armed || o == OrientationEventListener.ORIENTATION_UNKNOWN) return
                val land = (o in 60..120) || (o in 240..300)
                val port = o <= 30 || o >= 330 || (o in 150..210)
                if ((wantLandscape && land) || (!wantLandscape && port)) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    armed = false
                }
            }
        }
        prefs.getString("theme_uri", null)?.let { loadTheme(Uri.parse(it), false) }
    }
    override fun onStart() {
        super.onStart(); synth.start()
        if (orient.canDetectOrientation()) orient.enable()
    }
    override fun onStop() {
        orient.disable(); synth.gate = false; synth.stop(); super.onStop()
    }

    private fun toggleRotate() {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        wantLandscape = !isLand
        armed = true
        requestedOrientation = if (wantLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    // ---------- tema (imagem de fundo) ----------
    private fun pickTheme() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { startActivityForResult(i, reqTheme) }
        catch (e: Exception) { Toast.makeText(this, "Não achei um seletor de imagens", Toast.LENGTH_SHORT).show() }
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == reqTheme && res == RESULT_OK) {
            val u = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            loadTheme(u, true)
        }
    }
    private fun loadTheme(uri: Uri, save: Boolean) {
        Thread {
            val bmp = decode(uri)
            runOnUiThread {
                if (bmp != null) {
                    view.applyTheme(bmp)
                    if (save) prefs.edit().putString("theme_uri", uri.toString()).apply()
                } else if (save) {
                    Toast.makeText(this, "Não consegui abrir essa imagem", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    private fun decode(uri: Uri): Bitmap? {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (max(o.outWidth, o.outHeight) / s > 1600) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            var rot = 1
            try {
                contentResolver.openInputStream(uri)?.use {
                    rot = android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
                }
            } catch (e: Exception) {}
            val deg = when (rot) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            if (deg != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
            return bmp
        } catch (e: Throwable) {
            return null
        }
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
