package com.badger454.onethreefive.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.time.LocalDate

/**
 * Weekly automatic backups into a folder the user picks once (Storage Access Framework).
 * Files are named 1-3-5-auto-YYYY-MM-DD.json; only the newest [KEEP] are kept.
 * Settings live in this phone's preferences, not in the synced data.
 */
object AutoBackup {
    private const val PREFS = "autobackup"
    private const val PREFIX = "1-3-5-auto-"
    const val KEEP = 8
    private const val EVERY_DAYS = 7L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(ctx: Context): Uri? = prefs(ctx).getString("tree", null)?.let { Uri.parse(it) }
    fun lastDate(ctx: Context): String? = prefs(ctx).getString("last", null)

    /** Remember the folder picked with OpenDocumentTree and keep access to it across restarts. */
    fun setFolder(ctx: Context, tree: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
            Log.w("AutoBackup", "persist permission failed", e)
        }
        prefs(ctx).edit().putString("tree", tree.toString()).apply()
    }

    fun turnOff(ctx: Context) {
        folder(ctx)?.let { t ->
            try { ctx.contentResolver.releasePersistableUriPermission(t, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        }
        prefs(ctx).edit().remove("tree").remove("last").apply()
    }

    fun folderName(ctx: Context): String? {
        val tree = folder(ctx) ?: return null
        return try {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Back up if a folder is set and the last backup is a week old (or never). Returns true if a file was written. */
    fun runIfDue(ctx: Context, json: () -> String): Boolean {
        if (folder(ctx) == null) return false
        val last = lastDate(ctx)?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
        if (last != null && !LocalDate.now().isAfter(last.plusDays(EVERY_DAYS - 1))) return false
        return backUpNow(ctx, json())
    }

    /** Writes today's backup (replacing today's if it exists) and removes the oldest beyond [KEEP]. */
    fun backUpNow(ctx: Context, json: String): Boolean {
        val tree = folder(ctx) ?: return false
        val cr = ctx.contentResolver
        return try {
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = PREFIX + LocalDate.now() + ".json"
            val existing = list(ctx, tree)
            val target = existing.firstOrNull { it.first == name }?.second
                ?: DocumentsContract.createDocument(cr, root, "application/json", name)
                ?: return false
            cr.openOutputStream(target, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: return false
            prefs(ctx).edit().putString("last", LocalDate.now().toString()).apply()
            // prune: newest first by name (dates sort correctly)
            val all = list(ctx, tree).sortedByDescending { it.first }
            for ((_, uri) in all.drop(KEEP)) {
                try { DocumentsContract.deleteDocument(cr, uri) } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.w("AutoBackup", "backup failed", e)
            false
        }
    }

    /** (displayName, documentUri) of this app's automatic backups in the folder. */
    private fun list(ctx: Context, tree: Uri): List<Pair<String, Uri>> {
        val out = ArrayList<Pair<String, Uri>>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ctx.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(PREFIX) && name.endsWith(".json")) out.add(name to DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        }
        return out
    }
}
