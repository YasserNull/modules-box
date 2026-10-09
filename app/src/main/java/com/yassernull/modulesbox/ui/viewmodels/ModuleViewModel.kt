package com.yassernull.modulesbox.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassernull.modulesbox.R
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.data.repository.ModuleRepository
import com.yassernull.modulesbox.data.repository.ZipInstallResult
import com.yassernull.modulesbox.utils.ModuleInstaller
import com.yassernull.modulesbox.utils.PortManager
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
        viewModelScope.launch {
            ModuleInstaller.runningModulesFlow.collect { ports ->
                _runningModules.value = ports.mapValues { (id, port) ->
                    ModuleRunningState(port, id)
                }
            }
        }
    }

    fun refreshModules() {
        loadModules()
        val currentPorts = ModuleInstaller.getRunningPorts()
        _runningModules.value = currentPorts.mapValues { (id, port) ->
            ModuleRunningState(port, id)
        }
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

            if (ModuleInstaller.DEBUG_RUN_IN_TERMINAL) {
                // Debug: run visibly in the terminal so all output can be read.
                // Marked running so the Stop button + port UI appear; close the
                // terminal manually, then press Stop to release the port. A module
                // with its own start script reports its port over fd 4, which nothing
                // reads in this mode, so the port shown here is only the placeholder.
                val debugPort = PortManager.generateAvailablePort(context)
                if (debugPort == null || !PortManager.acquirePort(context, debugPort)) {
                    _startingModules.value = _startingModules.value - module.id
                    Toast.makeText(context, context.getString(R.string.module_start_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                ModuleInstaller.launchRunTerminal(context, module, ModuleInstaller.getServerCommand(module, debugPort))
                _runningModules.value = _runningModules.value + (module.id to ModuleRunningState(debugPort, module.id))
                _startingModules.value = _startingModules.value - module.id
                Toast.makeText(context, context.getString(R.string.module_started), Toast.LENGTH_SHORT).show()
                return@launch
            }

            // The port is resolved by ModuleInstaller: modules without a start script
            // get one allocated there, modules with one report it back themselves.
            val port = ModuleInstaller.startModuleServer(context, module)

            if (port != null) {
                _runningModules.value = _runningModules.value + (module.id to ModuleRunningState(port, module.id))
                Toast.makeText(context, context.getString(R.string.module_started), Toast.LENGTH_SHORT).show()
            } else {
                _resultDialogText.value = ModuleInstaller.getCommandOutput(module.id)
                _showResultDialog.value = true
            }

            _startingModules.value = _startingModules.value - module.id
        }
    }

    fun stopModule(context: Context, module: Module) {
        // Releasing the port also republishes used_ports.txt, so custom start scripts
        // see the freed port immediately.
        viewModelScope.launch { ModuleInstaller.stopModuleServer(context, module) }
        _runningModules.value = _runningModules.value - module.id
        Toast.makeText(context, context.getString(R.string.module_stopped), Toast.LENGTH_SHORT).show()
    }

    fun installModuleFromZip(uri: Uri, context: Context, onNeedsInstall: (Module) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.installFromZip(uri)) {
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
