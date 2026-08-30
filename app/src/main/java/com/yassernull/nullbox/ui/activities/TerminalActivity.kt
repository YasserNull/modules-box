package com.yassernull.nullbox.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.yassernull.nullbox.R
import com.yassernull.nullbox.core.AppPreferences
import com.yassernull.nullbox.core.LocaleManager
import com.yassernull.nullbox.core.preferences.terminal.getEffectiveWorkingMode
import com.yassernull.nullbox.core.preferences.terminal.getTerminalKeepScreenOn
import com.yassernull.nullbox.core.preferences.terminal.setTerminalKeepScreenOn
import com.yassernull.nullbox.services.SessionService
import com.yassernull.nullbox.ui.activities.terminal.Rootfs
import com.yassernull.nullbox.ui.activities.terminal.TerminalSessionManager
import com.yassernull.nullbox.ui.activities.terminal.changeSession
import com.yassernull.nullbox.ui.activities.terminal.closeTerminalDrawer
import com.yassernull.nullbox.ui.activities.terminal.onVirtualKeysToggleDrawer
import com.yassernull.nullbox.ui.activities.terminal.terminalView
import com.yassernull.nullbox.ui.activities.terminal.virtualKeysView
import com.yassernull.nullbox.ui.activities.terminal.downloader.Downloader
import com.yassernull.nullbox.ui.dialogs.terminal.TerminalModeDialog
import com.yassernull.nullbox.ui.components.TerminalDrawer
import com.yassernull.nullbox.ui.terminal.virtualkeys.SpecialButton
import com.yassernull.nullbox.ui.terminal.virtualkeys.VirtualKeysConstants
import com.yassernull.nullbox.ui.terminal.virtualkeys.VirtualKeysInfo
import com.yassernull.nullbox.ui.terminal.virtualkeys.VirtualKeysListener
import com.yassernull.nullbox.ui.terminal.virtualkeys.VirtualKeysView
import com.yassernull.nullbox.ui.theme.Theme
import com.yassernull.nullbox.ui.viewmodels.ThemeViewModel
import com.yassernull.nullbox.ui.viewmodels.ThemeViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

// إعداد الأزرار الإضافية بنفس تنسيق null-code-ide: صفان من المفاتيح + صفحة إدخال نص.
const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"DRAWER\"," + "\n      \"display\": \"≡\"" + "\n    }," + "\n    {" + "\n      \"key\": \"SCROLL_LOCK\"," + "\n      \"display\": \"↑↓\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")

// يعرض الجلسات في ملء الشاشة عبر خدمة أمامية، مع درج للجلسات وأزرار إضافية،
// وبوابة تنزيل Alpine في أول تشغيل، ومربع حوار لاختيار وضع الجلسة.
class TerminalActivity : ComponentActivity(), TerminalSessionClient, TerminalViewClient {

    companion object {
        const val EXTRA_INSTALL_MODULE_ID = "extra_install_module_id"
        const val EXTRA_INSTALL_SCRIPT_PATH = "extra_install_script_path"
        const val EXTRA_INSTALL_MODULE_NAME = "extra_install_module_name"
        const val EXTRA_INSTALL_COMMAND = "extra_install_command"
        const val EXTRA_INSTALL_WORKING_DIR = "extra_install_working_dir"
    }

    var sessionBinder by mutableStateOf<SessionService.SessionBinder?>(null)
    private var isBound = false

    private var keepScreenOn by mutableStateOf(false)
    private var showAddDialog by mutableStateOf(false)

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val terminalTextColorArgb = Color.WHITE
    private val effectiveTerminalBackgroundColorArgb = Color.BLACK
    private val terminalCursorColorArgb = Color.WHITE

    // Soft-keyboard visibility + back-press debounce for robust back handling.
    private var isKeyboardVisible = false
    private var lastBackPressTime = 0L

    // تطبيق اللغة قبل إنشاء الواجهة.
    override fun attachBaseContext(newBase: Context) {
        val appLanguage = runBlocking { LocaleManager.getSavedLanguage(newBase) }
        val localeContext = LocaleManager.applyLocaleToContext(newBase, appLanguage)
        super.attachBaseContext(localeContext)
    }

    private val sessionConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sessionBinder = service as? SessionService.SessionBinder
            isBound = true
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    Rootfs.ensureBinaries(this@TerminalActivity)
                }
                Rootfs.recheck()
                ensureInitialSession()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sessionBinder = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = AppPreferences(this)
        keepScreenOn = preferences.getTerminalKeepScreenOn()

        // Back handling: the drawer-closing BackHandler in TerminalRoot takes precedence
        // while the drawer is open. This callback handles the rest — close the drawer,
        // hide the soft keyboard first, then leave the terminal (see handleBackPress()).
        onBackPressedDispatcher.addCallback(this) {
            handleBackPress()
        }

        startAndBindService()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(preferences))
            Theme(
                currentTheme = themeViewModel.currentTheme.value,
                isBlackThemeEnabled = themeViewModel.isBlackThemeEnabled.value,
                isMaterialYouEnabled = themeViewModel.isMaterialYouEnabled.value,
                hueShift = themeViewModel.hueShift.value,
                saturationShift = themeViewModel.saturationShift.value
            ) {
                val binder = sessionBinder
                val service = binder?.getService()
                val sessionIds = service?.sessionList?.keys?.toList() ?: emptyList()
                val sessions = sessionIds.mapNotNull { sessionBinder?.getSession(it) }
                val currentSessionIndex = sessionIds.indexOf(service?.currentSession?.value?.first).coerceAtLeast(0)

                if (Rootfs.isSetupComplete(this@TerminalActivity)) {
                    TerminalRoot(
                        sessions = sessions,
                        sessionIds = sessionIds,
                        sessionDisplayNames = service?.sessionDisplayNames ?: emptyMap(),
                        sessionModes = service?.sessionList ?: emptyMap(),
                        currentSessionIndex = currentSessionIndex,
                        keepScreenOn = keepScreenOn,
                        onSelectSession = { index -> switchToSession(sessionIds.getOrNull(index)) },
                        onAddSession = { showAddDialog = true },
                        onDeleteSession = { index ->
                            sessionIds.getOrNull(index)?.let { sessionBinder?.terminateSession(it) }
                        },
                        onKillProcess = { sessions.getOrNull(currentSessionIndex)?.finishIfRunning() },
                        onToggleKeepScreenOn = { toggleKeepScreenOn() },
                        onReset = { sessions.getOrNull(currentSessionIndex)?.reset() },
                        createTerminalView = { createTerminalView() },
                        registerDrawerToggle = { toggle -> onVirtualKeysToggleDrawer = toggle },
                        onVirtualKeysViewCreated = { keysView ->
                            virtualKeysView = WeakReference(keysView)
                            bindVirtualKeys(keysView)
                        },
                        writeToSession = { text ->
                            terminalView.get()?.mTermSession?.let { session ->
                                val bytes = text.toByteArray(Charsets.UTF_8)
                                session.write(bytes, 0, bytes.size)
                            }
                        },
                        dispatchEnter = {
                            val view = terminalView.get()
                            view?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                            view?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                        }
                    )
                } else {
                    Downloader(
                        terminalActivity = this@TerminalActivity
                    )
                }

                if (showAddDialog) {
                    TerminalModeDialog(
                        onSelectMode = { mode ->
                            createSession(mode)
                            showAddDialog = false
                        },
                        onDismiss = { showAddDialog = false }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Track soft-keyboard visibility so back can hide it first (standard behaviour)
        // before leaving the terminal. adjustResize (manifest) resizes the window, so the
        // visible display frame shrinks when the keyboard is shown.
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            isKeyboardVisible = keypadHeight > screenHeight * 0.15
        }
    }

    // Catch the back key at the activity level for 3-button navigation / physical back.
    // On gesture navigation the OnBackPressedDispatcher callback (registered in onCreate)
    // is invoked by the system instead, so both paths converge on handleBackPress().
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                handleBackPress()
            }
            // Consume back here so the framework's default handling (which could also
            // invoke the dispatcher) is skipped on this path — no double handling.
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBackPress() {
        // Debounce: a single press may reach both dispatchKeyEvent and the dispatcher.
        val now = SystemClock.uptimeMillis()
        if (now - lastBackPressTime < 300) return
        lastBackPressTime = now

        // 1) Close the session drawer if it is open.
        if (closeTerminalDrawer?.invoke() == true) return

        // 2) Standard behaviour: first back hides the soft keyboard instead of leaving.
        if (isKeyboardVisible) {
            terminalView.get()?.let {
                val imm = getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(it.windowToken, 0)
            }
            return
        }

        // 3) Otherwise leave the terminal and return to the main screen.
        finish()
    }

    private fun startAndBindService() {
        val intent = Intent(this, SessionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, sessionConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (isBound) {
            runCatching { unbindService(sessionConnection) }
            isBound = false
        }
        sessionScope.cancel()
        terminalView.clear()
        virtualKeysView.clear()
        onVirtualKeysToggleDrawer = null
        super.onDestroy()
    }

    private fun ensureInitialSession() {
        val binder = sessionBinder ?: return
        // Don't create a session before the first-run downloader has finished — a proot
        // session would be created without a rootfs. createTerminalView() re-checks once
        // Rootfs.isDownloaded flips and the terminal UI is composed.
        if (!Rootfs.isSetupComplete(this)) return
        if (binder.getService().sessionList.isEmpty()) {
            val preferences = AppPreferences(this)
            createSession(preferences.getEffectiveWorkingMode())
        }
    }

    private fun createSession(workingMode: Int) {
        TerminalSessionManager(
            activity = this,
            sessionBinder = sessionBinder,
            scope = sessionScope,
            terminalTextColorArgb = terminalTextColorArgb,
            effectiveTerminalBackgroundColorArgb = effectiveTerminalBackgroundColorArgb,
            terminalCursorColorArgb = terminalCursorColorArgb
        ).createSession(workingMode)
    }

    private fun switchToSession(sessionId: String?) {
        if (sessionId == null) return
        changeSession(
            this,
            session_id = sessionId,
            onBackgroundColorArgb = terminalTextColorArgb,
            backgroundColorArgb = effectiveTerminalBackgroundColorArgb,
            cursorColorArgb = terminalCursorColorArgb
        )
    }

    private fun createTerminalView(): TerminalView {
        val view = TerminalView(this, null)
        view.setTerminalViewClient(this)
        view.setTextSize(14f * resources.displayMetrics.scaledDensity)
        view.setFocusable(true)
        view.setFocusableInTouchMode(true)
        view.setBackgroundColor(Color.BLACK)
        view.keepScreenOn = keepScreenOn
        terminalView = WeakReference(view)
        val binder = sessionBinder
        if (binder != null) {
            val service = binder.getService()
            if (service.sessionList.isNotEmpty()) {
                val currentId = service.currentSession.value.first
                if (binder.getSession(currentId) != null) {
                    changeSession(
                        this,
                        session_id = currentId,
                        onBackgroundColorArgb = terminalTextColorArgb,
                        backgroundColorArgb = effectiveTerminalBackgroundColorArgb,
                        cursorColorArgb = terminalCursorColorArgb
                    )
                } else {
                    ensureInitialSession()
                }
            } else {
                ensureInitialSession()
            }
        }
        view.post { view.updateSize() }
        return view
    }

    private fun toggleKeepScreenOn() {
        keepScreenOn = !keepScreenOn
        terminalView.get()?.keepScreenOn = keepScreenOn
        AppPreferences(this).setTerminalKeepScreenOn(keepScreenOn)
    }

    private fun showKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        terminalView.get()?.let { imm?.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    private fun pasteFromClipboard(session: TerminalSession) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip ?: return
        val text = clip.getItemAt(0).coerceToText(this) ?: return
        val bytes = text.toString().toByteArray(Charsets.UTF_8)
        session.write(bytes, 0, bytes.size)
    }

    // يربط الأزرار الإضافية بالجلسة الحالية؛ المستمع يقرأ mTermSession وقت الضغط دائماً.
    private fun bindVirtualKeys(keysView: VirtualKeysView) {
        val toggle = onVirtualKeysToggleDrawer
        keysView.virtualKeysViewClient = terminalView.get()?.let {
            VirtualKeysListener(it.mTermSession, it, toggle)
        }
    }

    // ============ TerminalSessionClient ============

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.get()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        val title = changedSession.title
        if (title.isNullOrEmpty()) setTitle(R.string.terminal)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        copyToClipboard(text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        if (session != null) pasteFromClipboard(session)
    }

    override fun onBell(session: TerminalSession) {
    }

    override fun onColorsChanged(session: TerminalSession) {
        terminalView.get()?.invalidate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        terminalView.get()?.invalidate()
    }

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
    }

    override fun getTerminalCursorStyle(): Int? = null

    // ============ TerminalViewClient ============

    override fun onScale(scale: Float): Float = scale.coerceIn(0.75f, 2.0f)

    override fun onSingleTapUp(e: MotionEvent) {
        showKeyboard()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.CTRL, false) ?: false

    override fun readAltKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.ALT, false) ?: false

    override fun readShiftKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.SHIFT, false) ?: false

    override fun readFnKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.FN, false) ?: false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        virtualKeysView.get()?.apply {
            readSpecialButton(SpecialButton.CTRL, true)
            readSpecialButton(SpecialButton.ALT, true)
            readSpecialButton(SpecialButton.SHIFT, true)
            readSpecialButton(SpecialButton.FN, true)
        }
        return false
    }

    override fun onEmulatorSet() {
        terminalView.get()?.setTerminalCursorBlinkerRate(0)
        terminalView.get()?.setTerminalCursorBlinkerState(false, false)
    }

    // ============ Shared log methods ============

    override fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, "Terminal error", e)
    }
}

// الحاوية الرئيسية للطرفية: درج الجلسات + الطرفية بملء الشاشة بدون شريط أدوات + أزرار إضافية.
@Composable
private fun TerminalRoot(
    sessions: List<TerminalSession>,
    sessionIds: List<String>,
    sessionDisplayNames: Map<String, String>,
    sessionModes: Map<String, Int>,
    currentSessionIndex: Int,
    keepScreenOn: Boolean,
    onSelectSession: (Int) -> Unit,
    onAddSession: () -> Unit,
    onDeleteSession: (Int) -> Unit,
    onKillProcess: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onReset: () -> Unit,
    createTerminalView: () -> TerminalView,
    registerDrawerToggle: (() -> Unit) -> Unit,
    onVirtualKeysViewCreated: (VirtualKeysView) -> Unit,
    writeToSession: (String) -> Unit,
    dispatchEnter: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val systemUiController = rememberSystemUiController()

    registerDrawerToggle {
        scope.launch {
            if (drawerState.isClosed) drawerState.open() else drawerState.close()
        }
    }

    // Expose the drawer to the activity's back handling: closing it from outside composition.
    DisposableEffect(Unit) {
        closeTerminalDrawer = {
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
                true
            } else {
                false
            }
        }
        onDispose { closeTerminalDrawer = null }
    }

    SideEffect {
        systemUiController.setStatusBarColor(color = Black, darkIcons = false)
        systemUiController.setNavigationBarColor(color = Black, darkIcons = false)
    }

    // Back is only intercepted to close the drawer. When the drawer is closed this handler
    // is disabled, so the Activity-level back handling (registered in onCreate and
    // dispatchKeyEvent) runs and leaves the terminal.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only let the drawer capture edge swipes while it is open. When it is closed the
        // system back gesture (and the terminal's edge swipes) are not intercepted.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            TerminalDrawer(
                sessions = sessions,
                sessionIds = sessionIds,
                sessionDisplayNames = sessionDisplayNames,
                sessionModes = sessionModes,
                currentSessionIndex = currentSessionIndex,
                keepScreenOn = keepScreenOn,
                onSelectSession = { index ->
                    onSelectSession(index)
                    scope.launch { drawerState.close() }
                },
                onAddSession = {
                    onAddSession()
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = onDeleteSession,
                onKillProcess = onKillProcess,
                onToggleKeepScreenOn = onToggleKeepScreenOn,
                onReset = onReset
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            AndroidView(
                factory = { createTerminalView() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            VirtualKeysSection(
                onVirtualKeysViewCreated = onVirtualKeysViewCreated,
                writeToSession = writeToSession,
                dispatchEnter = dispatchEnter
            )
        }
    }
}

// قسم الأزرار الإضافية أسفل الطرفية: صفحة أولى للمفاتيح وصفحة ثانية لإدخال نص، مثل null-code-ide.
@Composable
private fun VirtualKeysSection(
    onVirtualKeysViewCreated: (VirtualKeysView) -> Unit,
    writeToSession: (String) -> Unit,
    dispatchEnter: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(75.dp)
    ) { page ->
        when (page) {
            0 -> AndroidView(
                factory = { context ->
                    VirtualKeysView(context, null).apply {
                        onVirtualKeysViewCreated(this)
                        reload(
                            VirtualKeysInfo(
                                VIRTUAL_KEYS,
                                "",
                                VirtualKeysConstants.CONTROL_CHARS_ALIASES
                            )
                        )
                    }
                },
                update = { view -> onVirtualKeysViewCreated(view) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(75.dp)
            )

            1 -> {
                var text by rememberSaveable { mutableStateOf("") }

                AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            maxLines = 1
                            isSingleLine = true
                            imeOptions = EditorInfo.IME_ACTION_DONE

                            doOnTextChanged { textInput, _, _, _ ->
                                text = textInput.toString()
                            }

                            setOnEditorActionListener { v, actionId, event ->
                                if (actionId == EditorInfo.IME_ACTION_DONE) {
                                    if (text.isEmpty()) {
                                        dispatchEnter()
                                    } else {
                                        writeToSession(text)
                                        setText("")
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    },
                    update = { editText ->
                        if (editText.text.toString() != text) {
                            editText.setText(text)
                            editText.setSelection(text.length)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp)
                )
            }
        }
    }
}
