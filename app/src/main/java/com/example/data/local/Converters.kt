package com.example.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        return value.split(",").filter { it.isNotBlank() }
    }

    @TypeConverter
    fun toString(list: List<String>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun fromMap(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return value.split(",").associate {
            val parts = it.split("::")
            parts[0] to (parts.getOrNull(1) ?: "")
        }
    }

    @TypeConverter
    fun toMap(map: Map<String, String>): String {
        return map.entries.joinToString(",") { "${it.key}::${it.value}" }
    }
}
