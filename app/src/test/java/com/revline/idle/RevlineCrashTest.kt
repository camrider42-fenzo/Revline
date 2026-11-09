package com.revline.idle

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RevlineCrashTest {
    @Test fun aThrownLaunchIsWaitingOnTheNextOpen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.filesDir, RevlineCrash.FILE_NAME).delete()
        RevlineCrash.install(app)
        RevlineCrash.record(IllegalArgumentException("Cannot coerce value to an empty range: maximum 26.0 is less than minimum 27.047997."))
        val note = RevlineCrash.read(app)
        assertTrue(note!!.contains("empty range"))
        assertTrue(note.contains("RevlineCrashTest"))
        RevlineCrash.clear(app)
        assertNull(RevlineCrash.read(app))
        assertEquals(false, File(app.filesDir, RevlineCrash.FILE_NAME).exists())
    }
}
