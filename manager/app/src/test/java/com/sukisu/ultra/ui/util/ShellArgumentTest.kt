package com.sukisu.ultra.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShellArgumentTest {
    @Test
    fun preservesEmptyArguments() {
        assertEquals("''", "".shellArg())
    }

    @Test
    fun quotesWhitespaceAndShellMetacharactersLiterally() {
        assertEquals("'a b;\$(id)\n*'", "a b;\$(id)\n*".shellArg())
        assertEquals("'a'\\''b'", "a'b".shellArg())
    }

    @Test
    fun rejectsNullBytesInsteadOfTruncatingTheCommand() {
        assertThrows(IllegalArgumentException::class.java) { "a\u0000b".shellArg() }
    }
}
