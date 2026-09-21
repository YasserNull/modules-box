package com.yassernull.modulesbox.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassernull.modulesbox.data.model.Module
import com.yassernull.modulesbox.data.model.RemoteModule
import com.yassernull.modulesbox.data.repository.DownloadModuleResult
import com.yassernull.modulesbox.data.repository.RepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// حالة التنزيل لكل عنصر في المستودع.
enum class DownloadState { IDLE, DOWNLOADING, COMPLETED, FAILED }

// ViewModel لإدارة الوحدات المتاحة في المستودع عن بعد.
class RepoViewModel(private val repository: RepoRepository) : ViewModel() {

    private val _modules = MutableStateFlow<List<RemoteModule>>(emptyList())
    val modules: StateFlow<List<RemoteModule>> = _modules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // متغيرات حالة للتعامل مع الأخطاء وتصحيحها.
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _rawJsonForDebug = MutableStateFlow<String?>(null)
    val rawJsonForDebug: StateFlow<String?> = _rawJsonForDebug.asStateFlow()
    
    // تتبع حالة التنزيل لكل وحدة على حدة.
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()
    
    init {
        fetchRepoModules()
    }

    fun fetchRepoModules() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _rawJsonForDebug.value = null
            _modules.value = emptyList()

            // التعامل مع النتيجة التي قد تكون نجاحًا أو فشلًا.
            val result = repository.getRepoModules()
            
            if (result.modules != null) {
                // حالة النجاح.
                _modules.value = result.modules
            } else {
                // حالة الفشل.
                _modules.value = emptyList()
                _errorMessage.value = result.errorMessage
                _rawJsonForDebug.value = result.rawResponse
            }
            
            _isLoading.value = false
        }
    }

    fun downloadModule(module: RemoteModule, onComplete: () -> Unit, onNeedsInstall: (Module) -> Unit) {
        val repoUrl = module.repository
        if (_downloadStates.value[repoUrl] == DownloadState.DOWNLOADING) return

        viewModelScope.launch {
            _downloadStates.update { it + (repoUrl to DownloadState.DOWNLOADING) }

            when (val result = repository.downloadModule(module)) {
                is DownloadModuleResult.Downloaded -> {
                    _downloadStates.update { it + (repoUrl to DownloadState.COMPLETED) }
                    onComplete() // إعلام الواجهة باكتمال التنزيل لتحديث قائمة الوحدات المحلية.
                }

                is DownloadModuleResult.NeedsTerminalInstall -> {
                    // The module declares an install script: it must run in an Alpine
                    // terminal before it appears in the installed list.
                    _downloadStates.update { it + (repoUrl to DownloadState.COMPLETED) }
                    onNeedsInstall(result.module)
                }

                DownloadModuleResult.Failed -> {
                    _downloadStates.update { it + (repoUrl to DownloadState.FAILED) }
                }
            }
        }
    }
}