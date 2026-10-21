package cc.zohn.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputNamesTest {
    @Test fun bluRayNames() {
        for (n in listOf("BD", "bd", "Blu-ray", "BluRay", "Blu ray Player", "BDP", "DVD", "Sony BD Player")) {
            assertEquals(n, InputKind.BLU_RAY, inputKind(n, 4))
        }
    }

    @Test fun gameConsoleNames() {
        for (n in listOf("PlayStation 5", "PS4", "ps5", "Xbox", "XBOX Series X", "Nintendo", "Switch")) {
            assertEquals(n, InputKind.GAME_CONSOLE, inputKind(n, 4))
        }
    }

    @Test fun streamingNames() {
        for (n in listOf("Roku", "Fire TV", "FireTV", "Chromecast", "Apple TV", "SHIELD")) {
            assertEquals(n, InputKind.STREAMING_STICK, inputKind(n, 4))
        }
    }

    @Test fun nameRulesBeatTypeRules() {
        assertEquals(InputKind.BLU_RAY, inputKind("BD", 4))
        assertEquals(InputKind.BLU_RAY, inputKind("BD", 5))
        assertEquals(InputKind.GAME_CONSOLE, inputKind("Xbox", 3))
    }

    @Test fun typeRulesWhenNameUnknown() {
        assertEquals(InputKind.SOUNDBAR, inputKind("Living Room", 5))
        assertEquals(InputKind.SOUNDBAR, inputKind(null, 5))
        assertEquals(InputKind.CABLE_BOX, inputKind("Tuner", 3))
        assertEquals(InputKind.CABLE_BOX, inputKind(null, 3))
    }

    @Test fun unknownOtherwise() {
        assertEquals(InputKind.UNKNOWN, inputKind(null, null))
        assertEquals(InputKind.UNKNOWN, inputKind("Mystery", 4))
        assertEquals(InputKind.UNKNOWN, inputKind("", 0))
    }

    @Test fun matchesWholeTokensOnly() {
        assertEquals(InputKind.UNKNOWN, inputKind("Abdomen", 4))
        assertEquals(InputKind.UNKNOWN, inputKind("Wiishield", 4))
        assertEquals(InputKind.UNKNOWN, inputKind("Rokux", 4))
    }

    @Test fun displayNames() {
        assertEquals("Blu-ray", InputKind.BLU_RAY.display)
        assertEquals("Game console", InputKind.GAME_CONSOLE.display)
        assertEquals("Cable box", InputKind.CABLE_BOX.display)
        assertEquals("Streaming stick", InputKind.STREAMING_STICK.display)
        assertEquals("Soundbar", InputKind.SOUNDBAR.display)
        assertEquals("PC", InputKind.PC.display)
        assertEquals("Camera", InputKind.CAMERA.display)
    }

    @Test fun showTileRule() {
        assertTrue(showInputTile(hasCecDevice = true, connected = false, userNamed = false))
        assertTrue(showInputTile(hasCecDevice = false, connected = true, userNamed = false))
        assertTrue(showInputTile(hasCecDevice = false, connected = false, userNamed = true))
        assertFalse(showInputTile(hasCecDevice = false, connected = false, userNamed = false))
    }

    @Test fun titlePrefersUserThenKindThenCecThenPort() {
        assertEquals("Mine", inputTitle("Mine", InputKind.BLU_RAY, "BD", "HDMI 3"))
        assertEquals("Game console", inputTitle("GAME_CONSOLE", InputKind.BLU_RAY, "BD", "HDMI 3"))
        assertEquals("Blu-ray", inputTitle(null, InputKind.BLU_RAY, "BD", "HDMI 3"))
        assertEquals("Mystery", inputTitle(null, InputKind.UNKNOWN, "Mystery", "HDMI 3"))
        assertEquals("HDMI 3", inputTitle(null, InputKind.UNKNOWN, null, "HDMI 3"))
        assertEquals("HDMI 3", inputTitle("  ", InputKind.UNKNOWN, " ", "HDMI 3"))
    }

    @Test fun storedNameResolution() {
        assertEquals(InputKind.CAMERA, storedKind("CAMERA"))
        assertNull(storedKind("Mine"))
        assertNull(storedKind(null))
    }

    @Test fun menuLabel() {
        assertEquals("Blu-ray · HDMI 3", inputMenuLabel("Blu-ray", "HDMI 3"))
        assertEquals("HDMI 1", inputMenuLabel("HDMI 1", "HDMI 1"))
    }

    @Test fun orderByUseThenAlphabetical() {
        val items = listOf("zeta", "Blu-ray", "alpha", "mid")
        val counts = mapOf("mid" to 3, "Blu-ray" to 3, "zeta" to 1)
        assertEquals(listOf("Blu-ray", "mid", "zeta", "alpha"), sortedByUse(items, counts, { it }, { it }))
    }

    @Test fun portLabels() {
        assertEquals("HDMI 3", portLabel("HDMI3"))
        assertEquals("HDMI 1", portLabel("HDMI 1"))
        assertEquals("Living room", portLabel("Living room"))
    }
}
