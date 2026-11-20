package com.vknxworks.lijnctrl.data.saved

import com.vknxworks.lijnctrl.data.crypto.DataKeyStore
import com.vknxworks.lijnctrl.data.crypto.KeyWrapper
import com.vknxworks.lijnctrl.data.crypto.SealedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The saved data's move from the settings store into the encrypted file, and what happens when the key is gone. */
class SealedStoreTest {
    private fun tempDir(): File = Files.createTempDirectory("lijnctrl-store").toFile()

    /** Stands in for the Android Keystore: a wrap only its own [secret] can undo. */
    private class FakeWrapper(var secret: Byte = 7, var broken: Boolean = false) : KeyWrapper {
        override fun wrap(plain: ByteArray): ByteArray {
            if (broken) throw IllegalStateException("keystore unavailable")
            return byteArrayOf(secret) + plain.map { (it + secret).toByte() }
        }

        override fun unwrap(wrapped: ByteArray): ByteArray {
            if (broken) throw IllegalStateException("keystore unavailable")
            if (wrapped[0] != secret) throw IllegalStateException("wrong key")
            return wrapped.drop(1).map { (it - secret).toByte() }.toByteArray()
        }
    }

    /** Stands in for the settings store: one string, or nothing. */
    private class FakeOld(var value: String? = null, var complete: Boolean = true, var failClear: Boolean = false) : OldHome<String> {
        var clears = 0
        override suspend fun read() = OldHome.Found(value ?: "", value != null, complete)
        override suspend fun write(prev: String, next: String) {
            value = next
        }

        override suspend fun clear() {
            if (failClear) throw IllegalStateException("can't write the settings")
            clears++
            value = null
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private fun store(dir: File, old: FakeOld, wrapper: KeyWrapper = FakeWrapper()) = SealedStore(
        dir, "saved.json", DataKeyStore(dir, wrapper), old, "",
        encode = { it.toByteArray(Charsets.UTF_8) },
        decode = { bytes -> String(bytes, Charsets.UTF_8).also { if (it.startsWith("!")) throw IllegalArgumentException("damaged") } },
        scope = scope,
    )

    @Test
    fun oldValuesMoveIntoASealedFileAndAreRemovedAfterwards() = runBlocking {
        val dir = tempDir()
        val old = FakeOld("stops")
        val s = store(dir, old)
        s.awaitReady()
        assertEquals("stops", s.data.value)
        assertFalse(s.unreadable.value)
        assertTrue(SealedFile.isSealed(File(dir, "saved.json")))
        assertEquals(null, old.value)
        // Changes go to the file, not back to the settings store.
        s.update { "$it+1" }
        assertEquals(null, old.value)
        val again = store(dir, old)
        again.awaitReady()
        assertEquals("stops+1", again.data.value)
    }

    @Test
    fun withoutAKeyEverythingStaysInTheSettingsStore() = runBlocking {
        val dir = tempDir()
        val old = FakeOld("stops")
        val s = store(dir, old, FakeWrapper(broken = true))
        s.awaitReady()
        assertEquals("stops", s.data.value)
        s.update { "more" }
        assertEquals("more", old.value)
        assertFalse(File(dir, "saved.json").exists())
        assertFalse(File(dir, DataKeyStore.FILE).exists())
        // A later start with a working Keystore moves it.
        val later = store(dir, old)
        later.awaitReady()
        assertEquals("more", later.data.value)
        assertEquals(null, old.value)
        assertTrue(SealedFile.isSealed(File(dir, "saved.json")))
    }

    @Test
    fun valuesThatCantAllBeReadAreNotMoved() = runBlocking {
        val dir = tempDir()
        val old = FakeOld("part", complete = false)
        val s = store(dir, old)
        s.awaitReady()
        assertEquals("part", s.data.value)
        assertEquals("part", old.value)
        assertFalse(File(dir, "saved.json").exists())
    }

    @Test
    fun aMoveThatCantBeWrittenLeavesTheOldValuesInUse() = runBlocking {
        val dir = tempDir()
        // The temporary file can't be written (it's a folder with something in it).
        File(dir, "saved.json.tmp").apply { mkdirs(); File(this, "x").writeText("x") }
        val old = FakeOld("stops")
        val s = store(dir, old)
        s.awaitReady()
        assertEquals("stops", s.data.value)
        assertEquals("stops", old.value)
        assertFalse(File(dir, "saved.json").exists())
        s.update { "new" }
        assertEquals("new", old.value)
    }

    @Test
    fun oldValuesLeftBehindAfterTheMoveAreRemovedAtTheNextStart() = runBlocking {
        val dir = tempDir()
        val old = FakeOld("stops", failClear = true)
        val s = store(dir, old)
        s.awaitReady()
        assertEquals("stops", s.data.value)
        assertEquals("stops", old.value)
        assertTrue(File(dir, "saved.json").exists())
        old.failClear = false
        val next = store(dir, old)
        next.awaitReady()
        assertEquals("stops", next.data.value)
        assertEquals(null, old.value)
        // Old values that say something else than the file are never deleted, and the file wins.
        old.value = "something else"
        val third = store(dir, old)
        third.awaitReady()
        assertEquals("stops", third.data.value)
        assertEquals("something else", old.value)
    }

    @Test
    fun aFileThatCantBeOpenedIsNeverWrittenOverOrDeleted() = runBlocking {
        val dir = tempDir()
        val first = store(dir, FakeOld("stops"))
        first.awaitReady()
        val onDisk = File(dir, "saved.json").readBytes()
        val keyOnDisk = File(dir, DataKeyStore.FILE).readBytes()
        // The Keystore key is gone (another one answers).
        val old = FakeOld()
        val s = store(dir, old, FakeWrapper(secret = 9))
        s.awaitReady()
        assertTrue(s.unreadable.value)
        assertEquals("", s.data.value)
        s.update { "overwrite" }
        assertEquals("", s.data.value)
        assertArrayEquals(onDisk, File(dir, "saved.json").readBytes())
        assertArrayEquals(keyOnDisk, File(dir, DataKeyStore.FILE).readBytes())
        assertEquals(null, old.value)
        // Starting empty keeps both aside, and saving works again with a new key.
        s.startEmpty()
        assertFalse(s.unreadable.value)
        assertArrayEquals(onDisk, File(dir, "saved.json.unreadable").readBytes())
        assertArrayEquals(keyOnDisk, File(dir, DataKeyStore.FILE + ".unreadable").readBytes())
        s.update { "fresh" }
        assertTrue(SealedFile.isSealed(File(dir, "saved.json")))
        val again = store(dir, old, FakeWrapper(secret = 9))
        again.awaitReady()
        assertEquals("fresh", again.data.value)
    }

    @Test
    fun aDamagedFileIsUnreadableToo() = runBlocking {
        val dir = tempDir()
        File(dir, "saved.json").writeText("!not saved data")
        val s = store(dir, FakeOld())
        s.awaitReady()
        assertTrue(s.unreadable.value)
        s.update { "x" }
        assertEquals("!not saved data", File(dir, "saved.json").readText())
    }

    @Test
    fun anOrdinaryFileIsReadAndThenSealed() = runBlocking {
        val dir = tempDir()
        File(dir, "saved.json").writeText("plain")
        val s = store(dir, FakeOld())
        s.awaitReady()
        assertEquals("plain", s.data.value)
        assertTrue(SealedFile.isSealed(File(dir, "saved.json")))
    }

    @Test
    fun aCompleteTemporaryCopyIsPutBackWhenTheFileIsMissing() = runBlocking {
        val dir = tempDir()
        val first = store(dir, FakeOld("stops"))
        first.awaitReady()
        assertTrue(File(dir, "saved.json").renameTo(File(dir, "saved.json.tmp")))
        val s = store(dir, FakeOld())
        s.awaitReady()
        assertEquals("stops", s.data.value)
        assertTrue(File(dir, "saved.json").exists())
    }
}
