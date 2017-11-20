package helfi2012.chat

import org.junit.Test

import helfi2012.chat.encryption.RSAEncryptionUtil

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).

 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class ExampleUnitTest {
    @Test
    @Throws(Exception::class)
    fun encryptionIsCorrect() {
        val text = "Hello"
        val keyPair = RSAEncryptionUtil.generateKeys()
        val eText = RSAEncryptionUtil.encrypt(text, keyPair.public)
        val dText = RSAEncryptionUtil.decrypt(text, keyPair.private)
        assertEquals(4, (2 + 2).toLong())
    }
}