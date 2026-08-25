package com.example.data.local

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val trimmed = value.trim()
        if (trimmed.startsWith("[")) {
            return try {
                val jsonArray = JSONArray(trimmed)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                list
            } catch (e: Exception) {
                value.split(",").filter { it.isNotBlank() }
            }
        }
        return value.split(",").filter { it.isNotBlank() }
    }

    @TypeConverter
    fun toString(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(item)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun fromMap(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        val trimmed = value.trim()
        if (trimmed.startsWith("{")) {
            return try {
                val jsonObject = JSONObject(trimmed)
                val map = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.optString(key, "")
                }
                map
            } catch (e: Exception) {
                parseLegacyMap(value)
            }
        }
        return parseLegacyMap(value)
    }

    private fun parseLegacyMap(value: String): Map<String, String> {
        return value.split(",").mapNotNull {
            val parts = if (it.contains("::")) it.split("::") else it.split(":")
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                parts[0] to (parts.getOrNull(1) ?: "")
            } else null
        }.toMap()
    }

    @TypeConverter
    fun toMap(map: Map<String, String>?): String {
        if (map.isNullOrEmpty()) return "{}"
        val jsonObject = JSONObject()
        for ((key, value) in map) {
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }
}
