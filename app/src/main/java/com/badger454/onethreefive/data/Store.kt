package com.badger454.onethreefive.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Saves the whole app state as one JSON file (the same format as the backup export).
 * Writes are atomic and happen off the main thread, newest wins.
 */
class Store(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "state.json"))
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var pending: String? = null

    fun load(): String? = try {
        if (file.baseFile.exists()) String(file.readFully(), Charsets.UTF_8) else null
    } catch (e: Exception) {
        Log.e("Store", "Could not read saved data", e)
        null
    }

    fun save(json: String) {
        pending = json
        io.execute {
            val data = pending ?: return@execute
            pending = null
            write(data)
        }
    }

    /** Synchronous save, for broadcast receivers that may be killed right after returning. */
    fun saveNow(json: String) {
        pending = null
        write(json)
    }

    @Synchronized
    private fun write(json: String) {
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e("Store", "Could not open data file", e)
            return
        }
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e("Store", "Could not save data", e)
        }
    }
}
