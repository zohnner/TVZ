package cc.zohn.tvlauncher

/**
 * People who share the TV. Profiles live inside the launcher (not Google TV profiles): same apps
 * and accounts underneath, separate look, most-used order and hidden apps.
 * Palettes come from Moods (per phase of the day).
 */
enum class Profile(
    val id: String,
    val title: String,
    // Daily painting as wallpaper, art fact card, idle gallery.
    val artFocus: Boolean,
) {
    ZOHN("zohn", "Zohn", artFocus = false),
    SYDNEY("sydney", "Sydney", artFocus = true);

    val wordmark get() = title.uppercase()

    fun next(): Profile = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): Profile = entries.firstOrNull { it.id == id } ?: ZOHN
    }
}
