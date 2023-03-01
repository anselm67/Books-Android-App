package com.anselm.books.ui.sync

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.Constants
import java.time.Instant
import java.util.*

class SyncConfig(
    context: Context
) {
    private val prefs = context.getSharedPreferences(Constants.SYNC_PREFERENCES_NAME, MODE_PRIVATE)
    var folderId: String = ""
    private var lastSyncTimestamp: Long = -1

    init {
        load()
    }

    fun save(updateLastSync: Boolean = false) {
        if (updateLastSync) {
            lastSyncTimestamp = Instant.now().toEpochMilli()
        }
        val editor = prefs.edit()
        editor.putString("folderId", folderId)
        editor.putLong("lastSyncInstant", lastSyncTimestamp)
        editor.apply()
    }

    fun hasSynced(): Boolean {
        return lastSyncTimestamp > 0
    }

    fun lastSync(): Date {
        return Date(lastSyncTimestamp)
    }

    private fun load() {
        folderId = prefs.getString("folderId", "")!!
        lastSyncTimestamp = prefs.getLong("lastSyncInstant", 0L)
    }

    companion object {
        private var config: SyncConfig? = null

        @Synchronized
        fun get(): SyncConfig {
            if (config == null) {
                config = SyncConfig(app.applicationContext)
            }
            return config!!
        }
    }
}