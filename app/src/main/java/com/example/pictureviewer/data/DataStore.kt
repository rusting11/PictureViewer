package com.example.pictureviewer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DataStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("picture_viewer_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()

    companion object {
        private const val KEY_ENTRIES = "library_entries"
        private const val KEY_PROGRESS = "reading_progress"

        @Volatile
        private var INSTANCE: DataStore? = null

        fun getInstance(context: Context): DataStore {
            return INSTANCE ?: synchronized(this) {
                val instance = DataStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getAllComics(): List<LibraryEntry> {
        val entries = getAllEntries()
        val comics = entries.filter { it.isComic }.distinctBy { it.uri }
        android.util.Log.d("DataStore", "getAllComics: total=${entries.size}, comics=${comics.size}")
        return comics.sortedByDescending { it.lastModified }
    }

    fun getEntry(uri: String): LibraryEntry? {
        val entries = getAllEntries()
        return entries.find { it.uri == uri } 
            ?: entries.find { Uri.decode(it.uri) == uri }
            ?: entries.find { it.uri == Uri.encode(uri) }
    }

    fun getAllEntries(): List<LibraryEntry> {
        val json = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        val type = object : TypeToken<List<LibraryEntry>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveEntries(entries: List<LibraryEntry>) {
        val json = gson.toJson(entries)
        prefs.edit().putString(KEY_ENTRIES, json).apply()
    }

    suspend fun addEntries(newEntries: List<LibraryEntry>) = mutex.withLock {
        val existing = getAllEntries().toMutableList()
        val existingUris = existing.map { it.uri }.toSet()
        var added = 0
        var skipped = 0
        for (entry in newEntries) {
            if (entry.uri !in existingUris) {
                existing.add(entry)
                added++
            } else {
                skipped++
            }
        }
        android.util.Log.d("DataStore", "addEntries: added=$added, skipped=$skipped, total=${existing.size}")
        saveEntries(existing)
    }

    fun deleteEntry(uri: String) {
        val entries = getAllEntries().toMutableList()
        entries.removeAll { it.uri == uri || it.parentUri == uri || it.rootUri == uri }
        saveEntries(entries)
    }

    fun getProgress(comicUri: String): ReadingProgress? {
        val json = prefs.getString(KEY_PROGRESS, null) ?: return null
        val type = object : TypeToken<Map<String, ReadingProgress>>() {}.type
        val progressMap: Map<String, ReadingProgress> = try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
        return progressMap[comicUri]
    }

    fun saveProgress(progress: ReadingProgress) {
        val json = prefs.getString(KEY_PROGRESS, null)
        val type = object : TypeToken<MutableMap<String, ReadingProgress>>() {}.type
        val progressMap: MutableMap<String, ReadingProgress> = try {
            if (json != null) gson.fromJson(json, type) ?: mutableMapOf()
            else mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
        progressMap[progress.comicUri] = progress
        prefs.edit().putString(KEY_PROGRESS, gson.toJson(progressMap)).apply()
    }
}
