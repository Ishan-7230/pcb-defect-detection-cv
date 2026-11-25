package com.industry4.inspection

import android.app.Application
import com.industry4.inspection.ai.ModelManager
import com.industry4.inspection.data.database.AppDatabase
import com.industry4.inspection.data.repository.InspectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Industry4Application : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { InspectionRepository(this, database) }
    val modelManager by lazy { ModelManager(this) }

    override fun onCreate() {
        super.onCreate()
        // Asynchronously initialize AI model on startup
        applicationScope.launch {
            modelManager.initialize()
        }
    }
}
