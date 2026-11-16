package com.karelisio.allo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesVersions() {
        assertTrue(Updater.compare("1.2.0", "1.1.9") > 0)
        assertTrue(Updater.compare("v1.10.0", "1.9.0") > 0)
        assertEquals(0, Updater.compare("1.0", "1.0.0"))
        assertTrue(Updater.compare("0.1.0", "0.2.0") < 0)
    }

    @Test fun parsesLatestRelease() {
        val json = """{"tag_name":"v1.2.3","body":"## 1.2.3\n- Nouveau","assets":[
            {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":3},
            {"name":"Allo-v1.2.3.apk","browser_download_url":"https://x/Allo.apk","size":1234}]}"""
        val r = Updater.parse(json)!!
        assertEquals("1.2.3", r.version)
        assertEquals("https://x/Allo.apk", r.apkUrl)
        assertEquals(1234L, r.size)
    }

    @Test fun releaseWithoutApkIsIgnored() {
        assertNull(Updater.parse("""{"tag_name":"v1.0.0","assets":[]}"""))
    }
}
