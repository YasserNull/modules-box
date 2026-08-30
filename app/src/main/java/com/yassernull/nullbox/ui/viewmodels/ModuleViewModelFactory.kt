package com.yassernull.nullbox.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yassernull.nullbox.data.repository.ModuleRepository

// مصنع لإنشاء ModuleViewModel وتزويده بالتبعيات اللازمة.
class ModuleViewModelFactory(
    private val repository: ModuleRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModuleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModuleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
