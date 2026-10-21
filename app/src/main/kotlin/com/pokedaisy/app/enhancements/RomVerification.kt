package com.pokedaisy.app.enhancements

import java.io.InputStream
import java.security.MessageDigest

/** Hashes the entire selected ROM, including padding. Names and header codes are never proof. */
data class RomFingerprint(val sha256: String, val sha1: String, val bytes: Long,
    val nds: Boolean, val code: String?, val revision: Int?) {
    companion object {
        fun read(input: InputStream, expectedSize: Long): RomFingerprint? = runCatching {
            require(expectedSize in 0x200L..(512L shl 20))
            val sha256 = MessageDigest.getInstance("SHA-256")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val header = ByteArray(0x200)
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                check(n > 0 && size + n <= expectedSize)
                if (size < header.size) buffer.copyInto(header, size.toInt(), 0, minOf(n, header.size - size.toInt()))
                sha256.update(buffer, 0, n); sha1.update(buffer, 0, n); size += n
            }
            check(size == expectedSize)
            val nds = header[0x15C] == 0x56.toByte() && header[0x15D] == 0xCF.toByte()
            RomFingerprint(hex(sha256.digest()), hex(sha1.digest()), size, nds,
                if (nds) String(header, 0x0C, 4, Charsets.US_ASCII) else null,
                if (nds) header[0x1E].toInt() and 255 else null)
        }.getOrNull()
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}

data class VerifiedRevision(val id: String, val code: String, val revision: Int, val sha1: String)

/** Public checksum provenance: https://github.com/pret/pokeheartgold/blob/master/README.md
 * These identify retail images, not validated enhancement hooks. No patch is approved by this list. */
object RomVerification {
    val revisions = listOf(
        VerifiedRevision("heartgold-us-0", "IPKE", 0, "4fcded0e2713dc03929845de631d0932ea2b5a37"),
        VerifiedRevision("soulsilver-us-0", "IPGE", 0, "f8dc38ea20c17541a43b58c5e6d18c1732c7e582"),
    )
    fun match(rom: RomFingerprint?): VerifiedRevision? = rom?.takeIf { it.nds }?.let { r ->
        revisions.singleOrNull { it.code == r.code && it.revision == r.revision && it.sha1 == r.sha1 }
    }
    fun status(rom: RomFingerprint?): String = when {
        rom == null -> "ROM READ FAILED"
        !rom.nds || rom.code !in setOf("IPKE", "IPGE") -> "UNSUPPORTED GAME"
        match(rom) == null -> "UNKNOWN REVISION"
        else -> "KNOWN REVISION"
    }
}
