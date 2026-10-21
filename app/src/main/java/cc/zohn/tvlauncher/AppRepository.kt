package cc.zohn.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "TvLauncher"

data class AppEntry(
    val label: String,
    val packageName: String,
    val image: ImageBitmap,
    val isBanner: Boolean,
    // Tiny copy of the banner; stretched full-screen it reads as a blur without a GPU blur pass.
    val backdrop: ImageBitmap? = null,
)

private const val BANNER_W = 320
private const val BANNER_H = 180
private const val ICON_SIZE = 192
private const val BACKDROP_W = 32
private const val BACKDROP_H = 18

fun loadApps(pm: PackageManager, selfPackage: String): List<AppEntry> {
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != selfPackage }
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            val activity = info.activityInfo
            // ActivityInfo.loadBanner falls back to the application banner.
            val banner = activity.loadBanner(pm)
            AppEntry(
                label = info.loadLabel(pm).toString(),
                packageName = activity.packageName,
                image = (banner ?: info.loadIcon(pm)).toImage(banner != null),
                isBanner = banner != null,
                backdrop = banner?.toBitmap(BACKDROP_W, BACKDROP_H)?.asImageBitmap(),
            )
        }
}

fun launchIntentFor(pm: PackageManager, packageName: String): Intent? =
    pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)

private fun Drawable.toImage(isBanner: Boolean): ImageBitmap =
    if (isBanner) toBitmap(BANNER_W, BANNER_H).asImageBitmap()
    else toBitmap(ICON_SIZE, ICON_SIZE).asImageBitmap()

/**
 * Disk copy of the app list so the dock draws instantly on a cold start. Scanning packages and
 * decoding banners takes ~6 s on this TV; decoding cached PNGs is a fraction of that.
 */
object AppCache {
    private const val INDEX = "index.json"

    private fun dir(context: Context) = File(context.filesDir, "appcache")

    fun load(context: Context): List<AppEntry> = try {
        val dir = dir(context)
        val index = File(dir, INDEX)
        if (!index.exists()) {
            emptyList()
        } else {
            val entries = JSONArray(index.readText())
            (0 until entries.length()).mapNotNull { i ->
                val entry = entries.getJSONObject(i)
                val pkg = entry.getString("pkg")
                val image = BitmapFactory.decodeFile(File(dir, "$pkg.png").path) ?: return@mapNotNull null
                AppEntry(
                    label = entry.getString("label"),
                    packageName = pkg,
                    image = image.asImageBitmap(),
                    isBanner = entry.getBoolean("banner"),
                    backdrop = BitmapFactory.decodeFile(File(dir, "$pkg.bd.png").path)?.asImageBitmap(),
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "app cache unreadable", e)
        emptyList()
    }

    fun save(context: Context, apps: List<AppEntry>) {
        try {
            val dir = dir(context).apply { deleteRecursively(); mkdirs() }
            val entries = JSONArray()
            for (app in apps) {
                File(dir, "${app.packageName}.png").writePng(app.image)
                app.backdrop?.let { File(dir, "${app.packageName}.bd.png").writePng(it) }
                entries.put(
                    JSONObject()
                        .put("pkg", app.packageName)
                        .put("label", app.label)
                        .put("banner", app.isBanner)
                )
            }
            // Index last: a half-written cache has no index and is simply ignored.
            File(dir, INDEX).writeText(entries.toString())
        } catch (e: Exception) {
            Log.w(TAG, "app cache write failed", e)
        }
    }

    private fun File.writePng(image: ImageBitmap) = outputStream().use {
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
}
