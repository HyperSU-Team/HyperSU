package com.sukisu.ultra.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SulogFileNamesTest {
    @Test
    fun ignoresInvalidDatesAndOverflowingRotations() {
        assertEquals(
            listOf("sulog-2026-11-15-2.log", "sulog-2026-11-15.log", "sulog-2026-11-14.log"),
            parseSulogFileNames(listOf(
                "sulog-2026-11-14.log",
                "sulog-2026-11-15.log",
                "sulog-2026-11-15-2.log",
                "sulog-2026-02-30.log",
                "sulog-2026-11-15-999999999999999999999.log",
                "sulog-2026-11-15Xlog",
            )),
        )
    }
}
