package com.yassernull.nullbox.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassernull.nullbox.R
import com.yassernull.nullbox.data.model.Module
import com.yassernull.nullbox.data.repository.ModuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ViewModel لإدارة الوحدات المثبتة.
class ModuleViewModel(
    private val repository: ModuleRepository
) : ViewModel() {

    private val _modules = MutableStateFlow<List<Module>>(emptyList())
    val modules: StateFlow<List<Module>> = _modules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    fun installModuleFromZip(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = repository.installFromZip(uri, context)
            if (success) {
                Toast.makeText(context, context.getString(R.string.install_success), Toast.LENGTH_SHORT).show()
                refreshModules() // إعادة تحميل قائمة الوحدات لإظهار الوحدة الجديدة.
            } else {
                Toast.makeText(context, context.getString(R.string.install_failed), Toast.LENGTH_LONG).show()
            }
            _isLoading.value = false
        }
    }

    fun uninstallModule(module: Module) {
        viewModelScope.launch {
            if (repository.uninstallModule(module)) {
                refreshModules() // تحديث القائمة بعد الإزالة.
            }
        }
    }
}
