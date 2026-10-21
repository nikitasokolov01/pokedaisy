package com.pokedaisy.app.enhancements

import com.pokedaisy.app.RomIdentity
import java.io.*
import java.util.zip.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class RomVerificationTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun bytes() = ByteArray(4096) { (it % 251).toByte() }.also {
        "IPKE".toByteArray().copyInto(it, 12); it[30] = 0; it[0x15C] = 0x56; it[0x15D] = 0xCF.toByte()
    }
    @Test fun fullContentAndPaddingMatterAndHeaderSpoofsStayUnknown() {
        val a = bytes(); val first = RomFingerprint.read(a.inputStream(), a.size.toLong())!!
        assertEquals("IPKE", first.code); assertEquals(0, first.revision); assertTrue(first.nds)
        assertEquals("UNKNOWN REVISION", RomVerification.status(first))
        a[a.lastIndex] = 7
        val changed = RomFingerprint.read(a.inputStream(), a.size.toLong())!!
        assertNotEquals(first.sha256, changed.sha256); assertNotEquals(first.sha1, changed.sha1)
        assertNull(RomVerification.match(changed))
    }
    @Test fun zipAndRenamedPlainFileHaveTheSameIdentity() {
        val b = bytes(); val file = tmp.newFile("renamed.nds").apply { writeBytes(b) }
        val zip = tmp.newFile("packed.zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("another.nds")); it.write(b); it.closeEntry() }
        assertEquals(RomIdentity.enhancementFingerprint(file), RomIdentity.enhancementFingerprint(zip))
        assertEquals(b.toList(), file.readBytes().toList())
    }
    @Test fun shortOversizeAndUnreadableStreamsCannotYieldPartialHashes() {
        assertNull(RomFingerprint.read(bytes().inputStream(), 4097))
        assertNull(RomFingerprint.read(bytes().inputStream(), 4095))
        assertNull(RomFingerprint.read(bytes().inputStream(), 513L shl 20))
        assertNull(RomFingerprint.read(object : InputStream() { override fun read(): Int = throw IOException() }, 4096))
        assertNull(RomIdentity.enhancementFingerprint(File(tmp.root, "missing.nds")))
    }
    @Test fun catalogNeedsWholeHashHeaderAndRevisionTogether() {
        // Metadata fixture only: never presented as a real ROM integration test.
        for (r in RomVerification.revisions) {
            val f = RomFingerprint("a".repeat(64), r.sha1, 4096, true, r.code, r.revision)
            assertEquals(r, RomVerification.match(f))
            assertNull(RomVerification.match(f.copy(revision = 1)))
            assertNull(RomVerification.match(f.copy(code = "ABCD")))
            assertNull(RomVerification.match(f.copy(sha1 = "b".repeat(40))))
            assertNull(RomVerification.match(f.copy(nds = false)))
        }
        assertEquals("UNSUPPORTED GAME", RomVerification.status(RomFingerprint.read(ByteArray(512).inputStream(), 512)))
    }
}
