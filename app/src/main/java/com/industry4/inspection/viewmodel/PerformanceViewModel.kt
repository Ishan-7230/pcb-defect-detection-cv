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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PerformanceStats(
    val totalInspections: Int = 0,
    val avgInferenceLatencyMs: Float = 0.0f,
    val minInferenceLatencyMs: Float = 0.0f,
    val maxInferenceLatencyMs: Float = 0.0f,
    val avgEndToEndLatencyMs: Float = 0.0f,
    val avgFps: Float = 0.0f,
    val totalMemoryMb: Long = 0L,
    val freeMemoryMb: Long = 0L,
    val usedMemoryMb: Long = 0L
)

class PerformanceViewModel(
    private val modelManager: ModelManager,
    private val repository: InspectionRepository
) : ViewModel() {

    val modelMetadata: StateFlow<ModelMetadata?> = modelManager.metadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val performanceStats: StateFlow<PerformanceStats> = repository.allInspections.map { records ->
        computeStats(records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceStats())

    private fun computeStats(records: List<InspectionRecord>): PerformanceStats {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem

        if (records.isEmpty()) {
            return PerformanceStats(
                totalMemoryMb = totalMem,
                freeMemoryMb = freeMem,
                usedMemoryMb = usedMem
            )
        }

        val validLatencies = records.map { it.inferenceLatencyMs }.filter { it > 0 }
        val validE2E = records.map { it.endToEndLatencyMs }.filter { it > 0 }
        val validFps = records.map { it.fps }.filter { it > 0 }

        val avgInf = if (validLatencies.isNotEmpty()) validLatencies.average().toFloat() else 0f
        val minInf = if (validLatencies.isNotEmpty()) validLatencies.minOrNull() ?: 0f else 0f
        val maxInf = if (validLatencies.isNotEmpty()) validLatencies.maxOrNull() ?: 0f else 0f
        val avgE2E = if (validE2E.isNotEmpty()) validE2E.average().toFloat() else 0f
        val avgFps = if (validFps.isNotEmpty()) validFps.average().toFloat() else 0f

        return PerformanceStats(
            totalInspections = records.size,
            avgInferenceLatencyMs = avgInf,
            minInferenceLatencyMs = minInf,
            maxInferenceLatencyMs = maxInf,
            avgEndToEndLatencyMs = avgE2E,
            avgFps = avgFps,
            totalMemoryMb = totalMem,
            freeMemoryMb = freeMem,
            usedMemoryMb = usedMem
        )
    }

    companion object {
        fun provideFactory(
            modelManager: ModelManager,
            repository: InspectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PerformanceViewModel(modelManager, repository) as T
            }
        }
    }
}
