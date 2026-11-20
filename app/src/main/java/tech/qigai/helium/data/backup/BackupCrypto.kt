package tech.qigai.helium.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * M7 备份加密：用户口令派生密钥，不用 Android Keystore。
 *
 * 为什么不用 Keystore：Keystore 密钥与应用安装绑定，卸载 App 后密钥即失效，
 * 备份文件将永远无法解密——正好违背 M7 验收 2「备份 → 卸载 → 重装 → 恢复」。
 * 口令派生（PBKDF2-HmacSHA256，210,000 次 + AES-256-GCM）则与安装无关，
 * 只要口令对，任何一台装了 Helium 的手机都能恢复。代价是口令丢了就真的
 * 恢复不了，界面上必须明确告诉用户。
 *
 * 文件格式（全部二进制，无明文 JSON 落盘）：
 *   MAGIC(5) || salt(16) || iv(12) || AES-GCM 密文（含 128 位认证标签）
 * GCM 标签保证：口令错、文件被截断或改过一个字节，解密都会失败，而不是
 * 静默恢复出损坏的数据。
 */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCrypto {
    const val MIN_PASSPHRASE_LENGTH = 8
    const val ITERATIONS = 210_000
    const val KEY_BITS = 256
    const val SALT_LEN = 16
    const val IV_LEN = 12
    const val TAG_BITS = 128
    val MAGIC: ByteArray = "HLBK1".toByteArray(Charsets.US_ASCII)

    /** 口令太短时返回提示文案，否则返回 null。 */
    fun validatePassphrase(passphrase: CharArray): String? =
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) "口令至少 $MIN_PASSPHRASE_LENGTH 位，请设一个你能记住的口令" else null

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            MAGIC + salt + iv + cipher.doFinal(plain)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /** @throws BackupException 口令错误、文件损坏、截断或根本不是备份文件时抛出。 */
    fun decrypt(bytes: ByteArray, passphrase: CharArray): ByteArray {
        try {
            if (bytes.size < MAGIC.size + SALT_LEN + IV_LEN + 1) {
                throw BackupException("不是有效的备份文件（文件太短或已损坏）")
            }
            if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                throw BackupException("不是 Helium 备份文件")
            }
            val salt = bytes.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
            val iv = bytes.copyOfRange(MAGIC.size + SALT_LEN, MAGIC.size + SALT_LEN + IV_LEN)
            val ct = bytes.copyOfRange(MAGIC.size + SALT_LEN + IV_LEN, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(ct)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            // GCM 认证失败（口令错 / 密文被改）与格式错误都归到这里，绝不把原因细节
            // （如“口令错”vs“文件损坏”）区分给调用方以外的日志——界面只显示通用提示。
            throw BackupException("无法解密：口令不对，或备份文件已损坏", e)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        spec.clearPassword()
        return SecretKeySpec(key.encoded, "AES")
    }
}
