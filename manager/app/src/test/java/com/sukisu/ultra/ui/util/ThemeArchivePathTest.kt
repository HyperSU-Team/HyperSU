package com.sukisu.ultra.ui.util

import com.sukisu.ultra.ui.theme.ThemeArchive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeArchivePathTest {
    @Test
    fun acceptsRootFilesAndRelativeArchiveSubdirectories() {
        assertTrue(ThemeArchive.isSafeFileName("custom_font.ttf"))
        assertTrue(ThemeArchive.isSafeFileName("music track.mp3"))
        assertTrue(ThemeArchive.isSafeEntryName("music/track.mp3"))
        assertFalse(ThemeArchive.isSafeFileName("music/track.mp3"))
    }

    @Test
    fun rejectsTraversalAbsolutePathsAndNullBytes() {
        for (name in listOf("", ".", "..", "../shared_prefs/settings.xml", "/data/adb/test", "a/b", "a\\b", "a\u0000b")) {
            assertFalse(name, ThemeArchive.isSafeFileName(name))
        }
        assertFalse(ThemeArchive.isSafeEntryName("music/../track.mp3"))
        assertFalse(ThemeArchive.isSafeEntryName("music//track.mp3"))
        assertFalse(ThemeArchive.isSafeEntryName("track\u0000.mp3"))
    }
}
