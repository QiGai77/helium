package tech.qigai.helium.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.data.backup.BackupCrypto
import tech.qigai.helium.data.backup.BackupException

class BackupCryptoTest {
    private val plain = """{"format":"helium-backup","tables":{"memories":[{"id":1,"text":"对花生过敏"}]}}"""
        .toByteArray(Charsets.UTF_8)

    @Test fun `encrypt then decrypt round-trips`() {
        val enc = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, "correct horse".toCharArray()))
    }

    @Test fun `same plaintext encrypts differently each time (random salt and iv)`() {
        val a = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        val b = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assert(!a.contentEquals(b))
        assertArrayEquals(plain, BackupCrypto.decrypt(b, "correct horse".toCharArray()))
    }

    @Test fun `wrong passphrase is rejected, not silently decrypted`() {
        val enc = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(enc, "wrong phrase".toCharArray())
        }
    }

    @Test fun `one flipped ciphertext byte is rejected (GCM tag)`() {
        val enc = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 0x01).toByte()
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(enc, "correct horse".toCharArray())
        }
    }

    @Test fun `truncated file is rejected`() {
        val enc = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(enc.copyOf(enc.size / 2), "correct horse".toCharArray())
        }
    }

    @Test fun `random bytes and empty input are rejected`() {
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(ByteArray(64) { it.toByte() }, "correct horse".toCharArray())
        }
        assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(ByteArray(0), "correct horse".toCharArray())
        }
    }

    @Test fun `short passphrase is flagged`() {
        assertNull(BackupCrypto.validatePassphrase("12345678".toCharArray()))
        assertTrue(BackupCrypto.validatePassphrase("1234567".toCharArray()) != null)
        assertTrue(BackupCrypto.validatePassphrase("".toCharArray()) != null)
    }

    @Test fun `encrypt does not mutate the plaintext`() {
        val before = plain.copyOf()
        BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertArrayEquals(before, plain)
    }

    @Test fun `unrelated plaintext lengths round-trip`() {
        val empty = BackupCrypto.encrypt(ByteArray(0), "correct horse".toCharArray())
        assertEquals(0, BackupCrypto.decrypt(empty, "correct horse".toCharArray()).size)
        val big = ByteArray(100_000) { (it % 251).toByte() }
        val encBig = BackupCrypto.encrypt(big, "correct horse".toCharArray())
        assertArrayEquals(big, BackupCrypto.decrypt(encBig, "correct horse".toCharArray()))
    }
}
