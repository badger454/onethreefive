package com.badger454.onethreefive.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.badger454.onethreefive.BuildConfig
import com.badger454.onethreefive.domain.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from this app's GitHub Releases: check the latest release, download its APK,
 * and hand it to Android's installer (which always asks you to confirm).
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

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Downloads the APK (following GitHub's redirect) into the cache. Network call; run off the main thread. */
    fun download(ctx: Context, rel: Release, progress: (Float) -> Unit): File {
        var url = URL(rel.apkUrl)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "onethreefive-android")
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                val loc = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirect without location")
                url = URL(url, loc)
                hops++
                conn.disconnect()
                continue
            }
            if (code != 200) throw IllegalStateException("Download failed ($code)")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.apkSize
        val out = File(ctx.cacheDir, "update.apk")
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        return out
    }

    /** Hands the APK to Android's package installer. Android then shows its own "Update this app?" screen. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val status = Intent(ctx, InstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            val pi = PendingIntent.getBroadcast(ctx, id, status, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        }
    }
}

/** Receives the installer's status; when it needs the user's OK, opens Android's confirmation screen. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Log.w("Updater", "install failed: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
