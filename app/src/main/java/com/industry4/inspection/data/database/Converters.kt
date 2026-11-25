package com.industry4.inspection.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.industry4.inspection.data.models.DetectedDefectItem

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromDefectList(defects: List<DetectedDefectItem>?): String {
        return gson.toJson(defects ?: emptyList<DetectedDefectItem>())
    }

    @TypeConverter
    fun toDefectList(json: String?): List<DetectedDefectItem> {
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<DetectedDefectItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
