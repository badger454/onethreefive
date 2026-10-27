package com.badger454.onethreefive.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.badger454.onethreefive.BuildConfig
import com.badger454.onethreefive.domain.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from this app's GitHub Releases: check the latest release and open its APK in the browser.
 * The app itself never installs anything (no install permission), which keeps Play Protect happy.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/badger454/onethreefive/releases/latest"
    const val ACTION_INSTALL_STATUS = "com.badger454.onethreefive.INSTALL_STATUS"

    data class Release(val version: String, val build: Int, val notes: String, val apkUrl: String, val apkSize: Long)

    sealed class Check {
        data class Available(val release: Release) : Check()
        object UpToDate : Check()
        data class Failed(val message: String) : Check()
    }

    /** Build number from "v1.0.12" or "1.0.12" (the last number). */
    fun buildOf(version: String): Int = version.trimStart('v').substringAfterLast('.').toIntOrNull() ?: 0

    fun currentBuild(): Int = buildOf(BuildConfig.VERSION_NAME)

    /** Network call; run off the main thread. */
    @Suppress("UNCHECKED_CAST")
    fun check(): Check {
        return try {
            val c = URL(LATEST).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 15000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "onethreefive-android")
            val code = c.responseCode
            if (code == 404) return Check.Failed("No releases found. If the GitHub repository is private, the app can't see it.")
            if (code != 200) return Check.Failed("GitHub answered with error $code. Try again later.")
            val body = c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
            val root = Json.parse(body) as Map<String, Any?>
            val tag = root["tag_name"] as? String ?: return Check.Failed("The latest release has no version.")
            val assets = (root["assets"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
            val apk = assets.firstOrNull { (it["name"] as? String)?.endsWith(".apk") == true }
                ?: return Check.Failed("The latest release has no APK attached.")
            val rel = Release(
                version = tag.trimStart('v'),
                build = buildOf(tag),
                notes = (root["body"] as? String).orEmpty().trim(),
                apkUrl = apk["browser_download_url"] as? String ?: return Check.Failed("The APK has no download link."),
                apkSize = (apk["size"] as? Double)?.toLong() ?: -1L,
            )
            if (rel.build > currentBuild()) Check.Available(rel) else Check.UpToDate
        } catch (e: Exception) {
            Log.w("Updater", "check failed", e)
            Check.Failed("Couldn't reach GitHub. Check your connection and try again.")
        }
    }

    /** Opens the APK link in the browser; Chrome downloads it and Android's installer takes over from there. */
    fun openDownload(ctx: Context, rel: Release) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
