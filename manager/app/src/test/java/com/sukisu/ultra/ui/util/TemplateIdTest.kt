package com.sukisu.ultra.ui.util

import com.sukisu.ultra.ui.screen.templateeditor.isValidTemplateId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateIdTest {
    @Test
    fun acceptsNamesAndDottedIdentifiers() {
        assertTrue(isValidTemplateId("adb"))
        assertTrue(isValidTemplateId("com.example.Template_1"))
        assertTrue(isValidTemplateId("a".repeat(255)))
    }

    @Test
    fun rejectsPathsShellExpressionsAndNamesWithoutRoomForATerminator() {
        for (id in listOf("", ".", "..", "../profile", "/data/adb/test", "a/b", "a\\b", "a\$(id)", "a\u0000b", "a".repeat(256))) {
            assertFalse(id, isValidTemplateId(id))
        }
    }
}
