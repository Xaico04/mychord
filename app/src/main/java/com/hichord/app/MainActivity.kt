package com.hichord.app
import android.app.Activity
import android.os.Bundle
import android.view.*
import android.view.InputDevice

class MainActivity : Activity() {
    private val synth = Synth()
    private lateinit var view: HiChordView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = HiChordView(this, synth)
        setContentView(view)
    }
    override fun onStart() { super.onStart(); synth.start() }
    override fun onStop() { synth.gate = false; synth.stop(); super.onStop() }

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
