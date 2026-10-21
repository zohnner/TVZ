package cc.zohn.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import kotlin.random.Random

private const val TAG = "TvLauncher"

// Art Institute of Chicago public API: public-domain images, free, no key.
private const val SEARCH_URL = "https://api.artic.edu/api/v1/artworks/search"
private const val IIIF_URL = "https://www.artic.edu/iiif/2"
private const val FIELDS =
    "id,title,artist_title,date_display,image_id,short_description,description,artwork_type_title,is_public_domain"
private const val IMAGE_WIDTH = 1686 // AIC's recommended large size
// Whole sentences only, sized to fit the art card's three lines.
private const val MAX_FACT_CHARS = 190
private const val CACHED_IMAGES = 16

// Sydney's art: Impressionists, weighted toward Monet (Water Lilies is her profile's theme).
private val PAINTERS = listOf(
    "Claude Monet", "Pierre-Auguste Renoir", "Claude Monet", "Berthe Morisot", "Mary Cassatt",
    "Claude Monet", "Edgar Degas", "Vincent van Gogh", "Camille Pissarro", "Alfred Sisley",
    "Gustave Caillebotte", "Georges Seurat", "Paul Cézanne",
)

// Sydney's paintings follow the time of day (moods spec §3).
fun subjectsFor(phase: Phase): List<String> = when (phase) {
    Phase.MORNING -> listOf("sunrise", "morning")
    Phase.DAY -> listOf("garden", "water lilies", "river")
    Phase.EVENING -> listOf("sunset", "evening")
    Phase.NIGHT -> listOf("night", "moonlight", "nocturne")
}

fun pickIndex(epochDay: Long, phase: Phase, size: Int): Int =
    ((epochDay * 4 + phase.ordinal) % size).toInt()

// 2 = a subject is a whole word in the title, 1 = in the description, 0 = no match.
fun subjectScore(art: Artwork, subjects: List<String>): Int {
    fun hits(text: String?) = text != null && subjects.any {
        Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
    return when {
        hits(art.title) -> 2
        hits(art.fact) -> 1
        else -> 0
    }
}

data class Artwork(
    val id: Int,
    val title: String,
    val artist: String,
    val date: String,
    val imageId: String,
    val fact: String?,
)

object ArtRepository {
    private fun dir(context: Context) = File(context.filesDir, "art").apply { mkdirs() }

    /** Today's piece: the same all day, fetched once a day; yesterday's stays up while offline. */
    suspend fun daily(context: Context): Artwork? = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay()
        val cache = File(dir(context), "daily.json")
        val cached = readDaily(cache)
        if (cached?.first == today) return@withContext cached.second

        val painter = PAINTERS[(today % PAINTERS.size).toInt()]
        val works = search(painter) { it.contains(painter.substringAfterLast(' '), ignoreCase = true) }
        if (works.isEmpty()) return@withContext cached?.second
        val pick = works[((today / PAINTERS.size) % works.size).toInt()]
        try {
            cache.writeText(JSONObject().put("day", today).put("art", pick.toJson()).toString())
        } catch (e: Exception) {
            Log.w(TAG, "art cache write failed", e)
        }
        pick
    }

    /** A dozen pieces across four painters, in an order that's stable for the day. */
    suspend fun gallery(): List<Artwork> = withContext(Dispatchers.IO) {
        val seed = Random(LocalDate.now().toEpochDay())
        PAINTERS.distinct().shuffled(seed).take(4)
            .flatMap { painter -> search(painter) { it.contains(painter.substringAfterLast(' '), ignoreCase = true) }.shuffled(seed).take(3) }
            .shuffled(seed)
    }

    suspend fun image(context: Context, art: Artwork): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val dir = dir(context)
            val file = File(dir, "${art.imageId}.jpg")
            if (!file.exists()) {
                httpDownload("$IIIF_URL/${art.imageId}/full/$IMAGE_WIDTH,/0/default.jpg", file)
                prune(dir)
            }
            file.setLastModified(System.currentTimeMillis())
            // RGB_565 halves memory (~3.7 MB per painting); the TV has 1.8 GB RAM in total.
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "art image failed: ${art.imageId}", e)
            null
        }
    }

    private fun search(query: String, artistMatches: (String) -> Boolean): List<Artwork> = try {
        val data = httpGetJson(
            "$SEARCH_URL?q=${URLEncoder.encode(query, "UTF-8")}&limit=60&fields=$FIELDS"
        ).getJSONArray("data")
        (0 until data.length()).map { data.getJSONObject(it) }
            .filter {
                it.optBoolean("is_public_domain") &&
                    !it.isNull("image_id") &&
                    it.optString("artwork_type_title") == "Painting" &&
                    artistMatches(it.optString("artist_title"))
            }
            .map {
                Artwork(
                    id = it.getInt("id"),
                    title = it.optString("title"),
                    artist = it.optString("artist_title"),
                    date = it.optString("date_display"),
                    imageId = it.getString("image_id"),
                    fact = factFrom(it),
                )
            }
    } catch (e: Exception) {
        Log.w(TAG, "art search failed: $query", e)
        emptyList()
    }

    /**
     * This phase's piece for today. Fallback chain: subject match among the Impressionists ->
     * today's painter ([daily]) -> null (caller keeps what it has, or the pond placeholder).
     */
    suspend fun forPhase(context: Context, phase: Phase): Artwork? = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay()
        val cache = File(dir(context), "phase-${phase.name.lowercase()}.json")
        readDaily(cache)?.takeIf { it.first == today }?.let { return@withContext it.second }

        val works = subjectSearch(phase)
        // A fallback pick isn't cached, so the subject search is retried on the next load.
        if (works.isEmpty()) return@withContext daily(context)
        val pick = works[pickIndex(today, phase, works.size)]
        run {
            try {
                cache.writeText(JSONObject().put("day", today).put("art", pick.toJson()).toString())
            } catch (e: Exception) {
                Log.w(TAG, "art cache write failed", e)
            }
        }
        pick
    }

    /** Gallery for the current phase's subjects (falls back to the painter mix). */
    suspend fun gallery(phase: Phase): List<Artwork> = withContext(Dispatchers.IO) {
        val seed = Random(LocalDate.now().toEpochDay() * 4 + phase.ordinal)
        subjectSearch(phase).shuffled(seed).take(12).ifEmpty { gallery() }
    }

    // Impressionist paintings matching any of the phase's subjects, in a stable (id) order.
    private fun subjectSearch(phase: Phase): List<Artwork> {
        val surnames = PAINTERS.distinct().map { it.substringAfterLast(' ') }
        val subjects = subjectsFor(phase)
        val scored = subjects
            .flatMap { subject -> search(subject) { artist -> surnames.any { artist.contains(it, ignoreCase = true) } } }
            .distinctBy { it.id }
            .map { it to subjectScore(it, subjects) }
        // Only the best tier present: title hits if any, else description hits.
        val best = scored.maxOfOrNull { it.second } ?: 0
        if (best == 0) return emptyList()
        return scored.filter { it.second == best }.map { it.first }.sortedBy { it.id }
    }

    // First sentence or two of the museum's description, HTML stripped.
    private fun factFrom(json: JSONObject): String? {
        val raw = listOf("short_description", "description")
            .map { if (json.isNull(it)) "" else json.optString(it) }
            .firstOrNull { it.isNotBlank() } ?: return null
        val text = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
        val fact = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
            if (fact.isNotEmpty() && fact.length + sentence.length > MAX_FACT_CHARS) break
            if (fact.isNotEmpty()) fact.append(' ')
            fact.append(sentence)
        }
        return fact.toString().takeIf { it.isNotBlank() }
    }

    private fun prune(dir: File) {
        dir.listFiles { file -> file.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(CACHED_IMAGES)
            ?.forEach { it.delete() }
    }

    private fun readDaily(file: File): Pair<Long, Artwork>? = try {
        if (!file.exists()) null else {
            val json = JSONObject(file.readText())
            json.getLong("day") to json.getJSONObject("art").toArtwork()
        }
    } catch (e: Exception) {
        null
    }

    private fun Artwork.toJson() = JSONObject()
        .put("id", id).put("title", title).put("artist", artist).put("date", date)
        .put("imageId", imageId).put("fact", fact ?: JSONObject.NULL)

    private fun JSONObject.toArtwork() = Artwork(
        id = getInt("id"),
        title = getString("title"),
        artist = getString("artist"),
        date = getString("date"),
        imageId = getString("imageId"),
        fact = if (isNull("fact")) null else getString("fact"),
    )
}
