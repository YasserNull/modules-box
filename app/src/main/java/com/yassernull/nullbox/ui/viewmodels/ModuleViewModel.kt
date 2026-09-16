package com.yassernull.nullbox.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassernull.nullbox.R
import com.yassernull.nullbox.data.model.Module
import com.yassernull.nullbox.data.repository.ModuleRepository
import com.yassernull.nullbox.data.repository.ZipInstallResult
import com.yassernull.nullbox.utils.ModuleInstaller
import com.yassernull.nullbox.utils.PortManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModuleRunningState(
    val port: Int,
    val sessionId: String
)

class ModuleViewModel(
    private val repository: ModuleRepository
) : ViewModel() {

    private val _modules = MutableStateFlow<List<Module>>(emptyList())
    val modules: StateFlow<List<Module>> = _modules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _runningModules = MutableStateFlow<Map<String, ModuleRunningState>>(emptyMap())
    val runningModules: StateFlow<Map<String, ModuleRunningState>> = _runningModules.asStateFlow()

    private val _startingModules = MutableStateFlow<Set<String>>(emptySet())
    val startingModules: StateFlow<Set<String>> = _startingModules.asStateFlow()

    private val _showResultDialog = MutableStateFlow(false)
    val showResultDialog: StateFlow<Boolean> = _showResultDialog.asStateFlow()

    private val _resultDialogText = MutableStateFlow("")
    val resultDialogText: StateFlow<String> = _resultDialogText.asStateFlow()

    init {
        loadModules()
    }

    fun refreshModules() {
        loadModules()
    }

    private fun loadModules() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _modules.value = repository.getModules()
            } catch (e: Exception) {
                _modules.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun isModuleRunning(moduleId: String): Boolean {
        return _runningModules.value.containsKey(moduleId)
    }

    fun isModuleStarting(moduleId: String): Boolean {
        return _startingModules.value.contains(moduleId)
    }

    fun getModulePort(moduleId: String): Int? {
        return _runningModules.value[moduleId]?.port
    }

    fun startModule(context: Context, module: Module) {
        viewModelScope.launch {
            _startingModules.value = _startingModules.value + module.id

            val port = PortManager.generateAvailablePort(context)
            if (port == null) {
                _startingModules.value = _startingModules.value - module.id
                Toast.makeText(context, context.getString(R.string.module_start_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (!PortManager.acquirePort(context, port)) {
                _startingModules.value = _startingModules.value - module.id
                Toast.makeText(context, context.getString(R.string.module_start_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (ModuleInstaller.DEBUG_RUN_IN_TERMINAL) {
                // Debug: run visibly in the terminal so all output can be read.
                // Marked running so the Stop button + port UI appear; close the
                // terminal manually, then press Stop to release the port.
                ModuleInstaller.launchRunTerminal(context, module, ModuleInstaller.getServerCommand(module, port))
                _runningModules.value = _runningModules.value + (module.id to ModuleRunningState(port, module.id))
                _startingModules.value = _startingModules.value - module.id
                Toast.makeText(context, "تم تشغيل الوحدة", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val success = ModuleInstaller.startModuleServer(context, module, port)

            if (success) {
                _runningModules.value = _runningModules.value + (module.id to ModuleRunningState(port, module.id))
                Toast.makeText(context, "تم تشغيل الوحدة", Toast.LENGTH_SHORT).show()
            } else {
                PortManager.releasePort(context, port)
                _resultDialogText.value = ModuleInstaller.getCommandOutput(module.id)
                _showResultDialog.value = true
            }

            _startingModules.value = _startingModules.value - module.id
        }
    }

    fun stopModule(context: Context, module: Module) {
        ModuleInstaller.stopModuleServer(context, module)
        _runningModules.value = _runningModules.value - module.id
        Toast.makeText(context, context.getString(R.string.module_stopped), Toast.LENGTH_SHORT).show()
    }

    fun installModuleFromZip(uri: Uri, context: Context, onNeedsInstall: (Module) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.installFromZip(uri, context)) {
                is ZipInstallResult.Installed -> {
                    Toast.makeText(context, context.getString(R.string.install_success), Toast.LENGTH_SHORT).show()
                    refreshModules()
                }
                is ZipInstallResult.NeedsTerminalInstall -> {
                    onNeedsInstall(result.module)
                }
                is ZipInstallResult.Failed -> {
                    Log.w("ModuleViewModel", "installFromZip failed: ${result.reason}")
                    Toast.makeText(context, context.getString(R.string.install_failed), Toast.LENGTH_LONG).show()
                }
            }
            _isLoading.value = false
        }
    }

    fun uninstallModule(module: Module) {
        viewModelScope.launch {
            if (repository.uninstallModule(module)) {
                refreshModules()
            }
        }
    }

    fun getModuleLogs(moduleId: String): String {
        return ModuleInstaller.getCommandOutput(moduleId)
    }

    fun showResult(text: String) {
        _resultDialogText.value = text
        _showResultDialog.value = true
    }

    fun dismissResultDialog() {
        _showResultDialog.value = false
    }
}
