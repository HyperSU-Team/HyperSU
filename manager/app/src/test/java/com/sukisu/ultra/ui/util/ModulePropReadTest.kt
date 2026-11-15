package com.sukisu.ultra.ui.util

import com.sukisu.ultra.ui.screen.module.ModuleZipReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ModulePropReadTest {
    @Test
    fun preservesUtf8CharactersSplitAcrossReads() {
        val text = "name=中文模块\nauthor=作者\n"
        val stream = object : ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 1))
        }
        assertEquals(text, ModuleZipReader.readProp(stream))
    }

    @Test
    fun rejectsOversizedPropertiesInsteadOfReturningTruncatedMetadata() {
        assertThrows(IOException::class.java) {
            ModuleZipReader.readProp(ByteArrayInputStream(ByteArray(64 * 1024 + 1)))
        }
    }

    @Test
    fun acceptsPropertiesAtTheSizeLimit() {
        val text = "a".repeat(64 * 1024)
        assertEquals(text, ModuleZipReader.readProp(ByteArrayInputStream(text.toByteArray())))
    }
}
