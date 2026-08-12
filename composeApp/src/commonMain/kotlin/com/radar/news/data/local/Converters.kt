package com.radar.news.data.local

import androidx.room.TypeConverter

/**
 * `duplicateSourceIds` is a short list of registry ids (`bbc_ar`, `dw_ar`, …). Ids are
 * `[a-z0-9_]` by construction, so a newline-joined string is a safe encoding and stays
 * readable in a database inspector — no JSON dependency in the persistence layer.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String = value.orEmpty().joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val SEPARATOR = "\n"
    }
}
