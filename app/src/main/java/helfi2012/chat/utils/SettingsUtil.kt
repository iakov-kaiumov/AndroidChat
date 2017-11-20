package helfi2012.chat.utils

import android.content.Context
import helfi2012.chat.encryption.RSAEncryptionUtil
import helfi2012.chat.models.UserInformation
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.math.BigInteger
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

object SettingsUtil {
    private val SETTINGS_ADDRESS = "mysettings"
    private val SETTINGS_LOGIN = "client_login"
    private val SETTINGS_PASSWORD = "password"
    private val SETTINGS_AVATAR_PATH = "avatar_path"
    private val VALUE1 = "VALUE1"
    private val VALUE2 = "VALUE2"
    private val VALUE3 = "VALUE3"
    private val VALUE4 = "VALUE4"

    fun loadUserInformation(context: Context): UserInformation {
        val settings = context.getSharedPreferences(SETTINGS_ADDRESS, Context.MODE_PRIVATE)
        val user = UserInformation()
        user.login = settings.getString(SETTINGS_LOGIN, "")
        user.password = settings.getString(SETTINGS_PASSWORD, "")
        return user
    }

    fun saveUserInformation(context: Context, userInformation: UserInformation) {
        val editor = context.getSharedPreferences(SETTINGS_ADDRESS, Context.MODE_PRIVATE).edit()
        editor.putString(SETTINGS_LOGIN, userInformation.login)
        editor.putString(SETTINGS_PASSWORD, userInformation.password)
        editor.apply()
    }

    fun loadAvatarPath(context: Context): String {
        val settings = context.getSharedPreferences(SETTINGS_ADDRESS, Context.MODE_PRIVATE)
        return settings.getString(SETTINGS_AVATAR_PATH, "")
    }

    fun saveAvatarPath(context: Context, path: String) {
        val editor = context.getSharedPreferences(SETTINGS_ADDRESS, Context.MODE_PRIVATE).edit()
        editor.putString(SETTINGS_AVATAR_PATH, path)
        editor.apply()
    }

    fun saveRSAKeys(context: Context, login: String, publicKey: PublicKey, privateKey: PrivateKey) {
        val keySpecPublic = RSAEncryptionUtil.getPublicKeySpec(publicKey)
        val keySpecPrivate = RSAEncryptionUtil.getPrivateKeySpec(privateKey)
        val jsonObject = JSONObject()
        jsonObject.put(VALUE1, keySpecPublic.modulus.toString())
        jsonObject.put(VALUE2, keySpecPublic.publicExponent.toString())
        jsonObject.put(VALUE3, keySpecPrivate.modulus.toString())
        jsonObject.put(VALUE4, keySpecPrivate.privateExponent.toString())
        val editor = context.getSharedPreferences(SETTINGS_ADDRESS, Context.MODE_PRIVATE).edit()
        editor.putString(login, jsonObject.toJSONString())
        editor.apply()
    }

    fun loadRSAKeys(context: Context, login: String): KeyPair? {
        val settings = context.getSharedPreferences(SETTINGS_ADDRESS, Context.MODE_PRIVATE)
        val s = settings.getString(login, null)?: return null
        val jsonObject = JSONParser().parse(s) as JSONObject
        val publicKey = RSAEncryptionUtil.createPublicKey(
                BigInteger(jsonObject[VALUE1] as String), BigInteger(jsonObject[VALUE2] as String))
        val privateKey = RSAEncryptionUtil.createPrivateKey(
                BigInteger(jsonObject[VALUE3] as String), BigInteger(jsonObject[VALUE4] as String))
        return KeyPair(publicKey, privateKey)
    }
}