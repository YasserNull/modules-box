package com.yassernull.nullbox.ui.terminal.virtualkeys

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * مستمع الأزرار الإضافية: يكتب الحروف/الترميزات إلى جلسة الطرفية الحالية. الـ write يتم عبر
 * بايتات لأن الـ TerminalSession المحلي لا يوفر write(String).
 */
class VirtualKeysListener(
    private val session: TerminalSession?,
    private val terminalView: TerminalView?,
    private val onToggleDrawer: (() -> Unit)? = null
) : VirtualKeysView.IVirtualKeysView {

    override fun onVirtualKeyButtonClick(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?
    ) {
        val key = buttonInfo?.key ?: return
        when (key) {
            "DRAWER" -> {
                onToggleDrawer?.invoke()
                return
            }
            "SCROLL_LOCK" -> {
                terminalView?.mEmulator?.toggleAutoScrollDisabled()
                terminalView?.onScreenUpdated(true)
                return
            }
            "KEYBOARD" -> {
                val view = terminalView ?: return
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                if (imm != null) {
                    if (imm.isAcceptingText) {
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                    } else {
                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                return
            }
        }
        terminalView?.requestFocus()

        val keyCode = when (key) {
            "UP" -> KeyEvent.KEYCODE_DPAD_UP
            "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ENTER" -> KeyEvent.KEYCODE_ENTER
            "PGUP" -> KeyEvent.KEYCODE_PAGE_UP
            "PGDN" -> KeyEvent.KEYCODE_PAGE_DOWN
            "TAB" -> KeyEvent.KEYCODE_TAB
            "HOME" -> KeyEvent.KEYCODE_MOVE_HOME
            "END" -> KeyEvent.KEYCODE_MOVE_END
            "ESC" -> KeyEvent.KEYCODE_ESCAPE
            else -> null
        }

        if (keyCode != null && terminalView != null) {
            terminalView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            terminalView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            return
        }

        val writeable = when (key) {
            "UP" -> "\u001B[A"
            "DOWN" -> "\u001B[B"
            "LEFT" -> "\u001B[D"
            "RIGHT" -> "\u001B[C"
            "ENTER" -> "\u000D"
            "PGUP" -> "\u001B[5~"
            "PGDN" -> "\u001B[6~"
            "TAB" -> "\u0009"
            "HOME" -> "\u001B[H"
            "END" -> "\u001B[F"
            "ESC" -> "\u001B"
            else -> key
        }

        val activeSession = terminalView?.mTermSession ?: session
        if (activeSession != null) {
            val bytes = writeable.toByteArray(Charsets.UTF_8)
            activeSession.write(bytes, 0, bytes.size)
        }
    }

    override fun performVirtualKeyButtonHapticFeedback(
        view: View?,
        buttonInfo: VirtualKeyButton?,
        button: Button?
    ): Boolean {
        return false
    }
}
