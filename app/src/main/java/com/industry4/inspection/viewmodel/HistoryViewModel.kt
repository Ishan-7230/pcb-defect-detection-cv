package com.industry4.inspection.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.data.models.InspectionStatus
import com.industry4.inspection.data.repository.InspectionRepository
import com.industry4.inspection.utils.ExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    DEFECTS_ONLY,
    NORMAL_ONLY
}

class HistoryViewModel(
    private val repository: InspectionRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    val filteredInspections: StateFlow<List<InspectionRecord>> = combine(
        repository.allInspections,
        _filter
    ) { inspections, currentFilter ->
        when (currentFilter) {
            HistoryFilter.ALL -> inspections
            HistoryFilter.DEFECTS_ONLY -> inspections.filter { it.status == InspectionStatus.DEFECT_DETECTED }
            HistoryFilter.NORMAL_ONLY -> inspections.filter { it.status == InspectionStatus.NO_TRAINED_DEFECT_DETECTED }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: HistoryFilter) {
        _filter.value = filter
    }

    fun deleteInspection(id: String) {
        viewModelScope.launch {
            repository.deleteInspection(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun exportCsvReport(context: Context) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val result = repository.exportHistoryToCsv()
                result.onSuccess { uri ->
                    ExportManager.shareCsvReport(context, uri)
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    suspend fun getInspectionById(id: String): InspectionRecord? {
        return repository.getInspectionById(id)
    }

    fun formatSummary(record: InspectionRecord): String {
        return repository.formatInspectionSummary(record)
    }

    companion object {
        fun provideFactory(
            repository: InspectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(repository) as T
            }
        }
    }
}
