package com.sukisu.ultra.ui.webui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiOriginTest {
    @Test
    fun acceptsOnlyTheModuleHttpsOrigin() {
        assertTrue(isModuleWebUiUrl("https://mui.kernelsu.org/index.html"))
        assertTrue(isModuleWebUiUrl("https://mui.kernelsu.org:443/settings.html#tab"))
        for (url in listOf(
            "http://mui.kernelsu.org/",
            "https://mui.kernelsu.org:444/",
            "https://mui.kernelsu.org.example.test/",
            "https://mui.kernelsu.org@example.test/",
            "https://user@mui.kernelsu.org/",
            "https://example.test/",
            "file:///data/local/tmp/index.html",
            "content://example.test/index.html",
            "javascript:alert(1)",
            "data:text/html,test",
        )) {
            assertFalse(url, isModuleWebUiUrl(url))
        }
    }
}
