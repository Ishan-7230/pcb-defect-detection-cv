package com.industry4.inspection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.industry4.inspection.ai.ModelManager
import com.industry4.inspection.ai.ModelMetadata
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.data.repository.InspectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val modelManager: ModelManager,
    private val repository: InspectionRepository
) : ViewModel() {

    val modelMetadata: StateFlow<ModelMetadata?> = modelManager.metadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isModelReady: StateFlow<Boolean> = modelManager.isModelReady
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val totalInspections: StateFlow<Int> = repository.totalInspectionsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDefects: StateFlow<Int> = repository.totalDefectsDetected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentInspections: StateFlow<List<InspectionRecord>> = repository.recentInspections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun provideFactory(
            modelManager: ModelManager,
            repository: InspectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(modelManager, repository) as T
            }
        }
    }
}
