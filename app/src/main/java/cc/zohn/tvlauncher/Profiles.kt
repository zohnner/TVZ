package cc.zohn.tvlauncher

import cc.zohn.tvlauncher.ui.theme.LauncherPalette
import cc.zohn.tvlauncher.ui.theme.SydneyPalette
import cc.zohn.tvlauncher.ui.theme.ZohnPalette

/**
 * People who share the TV. Profiles live inside the launcher (not Google TV profiles): same apps
 * and accounts underneath, separate look, most-used order and hidden apps.
 */
enum class Profile(
    val id: String,
    val title: String,
    val palette: LauncherPalette,
    // Daily painting as wallpaper, art fact card, idle gallery.
    val artFocus: Boolean,
) {
    ZOHN("zohn", "Zohn", ZohnPalette, artFocus = false),
    SYDNEY("sydney", "Sydney", SydneyPalette, artFocus = true);

    val wordmark get() = title.uppercase()

    fun next(): Profile = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): Profile = entries.firstOrNull { it.id == id } ?: ZOHN
    }
}
