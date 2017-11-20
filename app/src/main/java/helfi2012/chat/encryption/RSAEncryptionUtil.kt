package helfi2012.chat.encryption

import android.util.Base64
import java.math.BigInteger
import javax.crypto.Cipher
import java.security.*
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.KeyFactory

object RSAEncryptionUtil {
    private val ALGORITHM = "RSA"
    private val SPLIT_SIGN = "|"
    private val SPLIT_PART = 20
    private val KEY_SIZE = 1024

    fun generateKeys(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance(ALGORITHM)
        keyGen.initialize(KEY_SIZE)
        return keyGen.generateKeyPair()
    }

    fun getPublicKeySpec(publicKey: PublicKey): RSAPublicKeySpec {
        val factory = KeyFactory.getInstance(ALGORITHM)
        return factory.getKeySpec(publicKey, RSAPublicKeySpec::class.java)
    }

    fun createPublicKey(modulus: BigInteger, publicExp: BigInteger): PublicKey {
        val factory = KeyFactory.getInstance(ALGORITHM)
        return factory.generatePublic(RSAPublicKeySpec(modulus, publicExp))
    }

    fun getPrivateKeySpec(privateKey: PrivateKey): RSAPrivateKeySpec {
        val factory = KeyFactory.getInstance(ALGORITHM)
        return factory.getKeySpec(privateKey, RSAPrivateKeySpec::class.java)
    }

    fun createPrivateKey(modulus: BigInteger, privateExp: BigInteger): PrivateKey {
        val factory = KeyFactory.getInstance(ALGORITHM)
        return factory.generatePrivate(RSAPrivateKeySpec(modulus, privateExp))
    }

    fun decrypt(s: String, privateKey: PrivateKey): String {
        val string = StringBuilder()
        s.split(SPLIT_SIGN)
                .filter { it.isNotEmpty() }
                .forEach { string.append(decryptPart(it, privateKey)) }
        return string.toString()
    }

    private fun decryptPart(text: String, privateKey: PrivateKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        val decodedStr = Base64.decode(text.replace("\n", "").replace("\r", "").replace(" ", ""), Base64.NO_WRAP)
        return String(cipher.doFinal(decodedStr))
    }

    fun encrypt(s: String, publicKey: PublicKey): String {
        val string = StringBuilder()
        for (i in 0..s.length/SPLIT_PART)
            string.append(encryptPart(s.substring(i * SPLIT_PART, minOf(s.length, (i + 1) * SPLIT_PART)), publicKey)).append(SPLIT_SIGN)
        return string.toString()
    }

    private fun encryptPart(text: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val plainText = cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(plainText, Base64.NO_WRAP)
    }
}