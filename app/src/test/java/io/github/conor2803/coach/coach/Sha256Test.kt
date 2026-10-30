package io.github.conor2803.coach.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {

    @Test
    fun `hashes a stream to lowercase hex`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".byteInputStream()),
        )
    }

    @Test
    fun `hashes an empty stream`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0).inputStream()),
        )
    }
}
