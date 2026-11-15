package com.sukisu.ultra.ui.util

import com.sukisu.ultra.ui.screen.install.InstallMethod
import com.sukisu.ultra.ui.screen.install.isReadyToInstall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReadinessTest {
    @Test
    fun canceledFilePickersCannotBecomeDirectInstalls() {
        assertFalse((null as InstallMethod?).isReadyToInstall)
        assertFalse(InstallMethod.SelectFile(summary = null).isReadyToInstall)
        assertFalse(InstallMethod.HorizonKernel().isReadyToInstall)
    }

    @Test
    fun downloadsNeedBothUrlAndPartition() {
        assertFalse(InstallMethod.DownloadFile(summary = null).isReadyToInstall)
        assertFalse(InstallMethod.DownloadFile(url = "https://example.test/ota", summary = null).isReadyToInstall)
        assertTrue(InstallMethod.DownloadFile(url = "https://example.test/ota", partition = "boot", summary = null).isReadyToInstall)
    }

    @Test
    fun directInstallMethodsRequireNoFile() {
        assertTrue(InstallMethod.DirectInstall.isReadyToInstall)
        assertTrue(InstallMethod.DirectInstallToInactiveSlot.isReadyToInstall)
    }
}
