package helfi2012.chat.tcpconnection

import helfi2012.chat.encryption.RSAEncryptionUtil
import helfi2012.chat.models.Attachment
import helfi2012.chat.models.ChatMessage
import helfi2012.chat.utils.Constants
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

import java.net.Socket
import java.io.*
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.util.*

class TCPClient(port: Int, host: String) {

    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var socket: Socket? = null
    private var resend: Resender? = null
    private val publicResponseQueue = ArrayDeque<JSONObject>()
    private val privateResponseQueue = ArrayDeque<JSONObject>()
    var isCreated = false
        private set
    var isAuthorize = false
        private set
    var mLogin: String = ""

    val response: JSONObject?
        get() = publicResponseQueue.poll()
    var externalStorageDirectory = ""

    var publicKey: PublicKey? = null

    init {
        try {
            this.socket = Socket(host, port)
            this.input = DataInputStream(socket!!.getInputStream())
            this.output = DataOutputStream(socket!!.getOutputStream())
            this.socket!!.soTimeout = Constants.SOCKET_TIMEOUT1
            this.resend = Resender()
            this.resend!!.start()
            isCreated = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createShortJSON(key: Any, value: Any): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put(key, value)
        return jsonObject
    }

    fun createFilePath(fileName: String): String =
            externalStorageDirectory + Constants.APP_PREFERENCES_DIRECTORY_BASE + fileName

    private fun getFileName(path: String): String = path.substring(path.lastIndexOf("/") + 1)

    fun createMessageFormJSON(jsonObject: JSONObject): ChatMessage {
        val chatMessage = ChatMessage(jsonObject[JSONKeys.JSON_KEY_MESSAGE_NAME] as String,
                jsonObject[JSONKeys.JSON_KEY_MESSAGE_TEXT] as String,
                jsonObject[JSONKeys.JSON_KEY_MESSAGE_TIME] as Long
        )
        val jsonArray = jsonObject[JSONKeys.JSON_KEY_MESSAGE_ATTACHMENTS] as JSONArray
        jsonArray
                .map { it as JSONObject }
                .forEach {
                    val name = it[JSONKeys.JSON_KEY_ATTACHMENT_NAME] as String
                    val ratio = it[JSONKeys.JSON_KEY_ATTACHMENT_RATIO] as Double
                    chatMessage.attachments.add(Attachment(name, createFilePath(name), ratio))
                }
        return chatMessage
    }

    fun sendMessage(thatLogin: String, text: String, paths: Array<String>) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_CLIENT_MESSAGE)
        jsonObject.put(JSONKeys.JSON_KEY_MESSAGE_NAME, thatLogin)
        jsonObject.put(JSONKeys.JSON_KEY_MESSAGE_TEXT, text)
        jsonObject.put(JSONKeys.JSON_KEY_MESSAGE_TIME, System.currentTimeMillis())
        val array = JSONArray()
        paths.mapTo(array) { getFileName(it) }
        jsonObject.put(JSONKeys.JSON_KEY_MESSAGE_ATTACHMENTS, array)
        printToServer(jsonObject.toJSONString())
    }

    private fun printToServer(s: String) = try {
        synchronized(output!!) {
            output!!.writeUTF(s)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    fun sendAttachment(path: String, onError: () -> Unit, onLoading: (progress: Int) -> Unit, onLoaded: () -> Unit) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_ATTACHMENT_SEND)
        jsonObject.put(JSONKeys.JSON_KEY_ATTACHMENT_NAME, getFileName(path))
        jsonObject.put(JSONKeys.JSON_KEY_ATTACHMENT_LENGTH, File(path).length().toString())
        printToServer(jsonObject.toJSONString())
        Thread({ sendFile(path) }).start()
        val maxProgress = 100
        while (true) {
            if (!isCreated) {
                onError.invoke()
                break
            }
            val inputJSONObject = privateResponseQueue.poll()?: continue
            val responseType = inputJSONObject[JSONKeys.JSON_KEY_RESPONSE_TYPE] as String
            if (responseType == ServerKeys.ON_ATTACHMENT_SENDING_PROGRESS) {
                try {
                    val total = inputJSONObject[JSONKeys.JSON_KEY_ATTACHMENT_PROGRESS] as Long
                    val length = inputJSONObject[JSONKeys.JSON_KEY_ATTACHMENT_LENGTH] as Long
                    val progress = (total.toDouble() / length * maxProgress).toInt()
                    onLoading.invoke(progress)
                    if (progress == maxProgress) {
                        onLoaded.invoke()
                        break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun sendFile(path: String) = synchronized (socket!!) {
        try {
            val file = File(path)
            val inF = FileInputStream(file)
            val length = file.length()
            val bytes = ByteArray(length.toInt())

            while (true) {
                val count = inF.read(bytes)
                if (count <= -1) break
                output!!.write(bytes, 0, count)
            }
            inF.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFile(path: String, length: Int) {
        println("loading file " + path)
        synchronized(socket!!) {
            try {
                val fos = FileOutputStream(path)
                val bytes = ByteArray(length)
                var count: Int
                var total = 0
                while (total != length) {
                    count = input!!.read(bytes, 0, length - total)
                    total += count
                    fos.write(bytes, 0, count)
                }
                fos.close()
                println("PATH OF THE FILE " + path)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getDialog(login: String, startIndex: Int, count: Int) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.GET_DIALOG)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        jsonObject.put(JSONKeys.JSON_KEY_START_INDEX, startIndex)
        jsonObject.put(JSONKeys.JSON_KEY_COUNT, count)
        printToServer(jsonObject.toJSONString())
    }

    fun getDialogs() = printToServer(createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.GET_DIALOGS).toJSONString())

    fun deleteMessages(login: String, startIndex: Long, count: Int) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.DELETE_MESSAGES)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        jsonObject.put(JSONKeys.JSON_KEY_START_INDEX, startIndex)
        jsonObject.put(JSONKeys.JSON_KEY_COUNT, count)
        printToServer(jsonObject.toJSONString())
    }

    fun createDialog(login: String, publicKey: PublicKey) {
        val keySpec = RSAEncryptionUtil.getPublicKeySpec(publicKey)
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.CREATE_DIALOG)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        jsonObject.put(JSONKeys.JSON_KEY_VALUE1, true)
        jsonObject.put(JSONKeys.JSON_KEY_VALUE2, keySpec.modulus.toString())
        jsonObject.put(JSONKeys.JSON_KEY_VALUE3, keySpec.publicExponent.toString())
        printToServer(jsonObject.toJSONString())
    }

    fun createDialog(login: String, encryptionKey: PublicKey, keyPair: KeyPair) {
        val keySpecPublic = RSAEncryptionUtil.getPublicKeySpec(keyPair.public)
        val keySpecPrivate = RSAEncryptionUtil.getPrivateKeySpec(keyPair.private)
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.CREATE_DIALOG)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        jsonObject.put(JSONKeys.JSON_KEY_VALUE1, false)
        jsonObject.put(JSONKeys.JSON_KEY_VALUE2, keySpecPublic.modulus.toString())
        jsonObject.put(JSONKeys.JSON_KEY_VALUE3, keySpecPublic.publicExponent.toString())
        jsonObject.put(JSONKeys.JSON_KEY_VALUE4, RSAEncryptionUtil.encrypt(keySpecPrivate.modulus.toString(), encryptionKey))
        jsonObject.put(JSONKeys.JSON_KEY_VALUE5, RSAEncryptionUtil.encrypt(keySpecPrivate.privateExponent.toString(), encryptionKey))
        printToServer(jsonObject.toJSONString())
    }

    fun getFile(fileName: String) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.GET_FILE)
        jsonObject.put(JSONKeys.JSON_KEY_ATTACHMENT_NAME, fileName)
        printToServer(jsonObject.toJSONString())
    }

    fun changeAvatar(login: String, path: String) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_CHANGE_AVATAR)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        jsonObject.put(JSONKeys.JSON_KEY_ATTACHMENT_NAME, getFileName(path))
        printToServer(jsonObject.toJSONString())
    }

    fun login(login: String, password: String) {
        mLogin = login
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_LOGIN)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        //jsonObject.put(JSONKeys.JSON_KEY_USER_PASSWORD, RSAEncryptionUtil.encrypt(password, publicKey!!))
        jsonObject.put(JSONKeys.JSON_KEY_USER_PASSWORD, password)
        printToServer(jsonObject.toJSONString())
    }

    fun register(login: String, password: String) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_REGISTER)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        jsonObject.put(JSONKeys.JSON_KEY_USER_PASSWORD, RSAEncryptionUtil.encrypt(password, publicKey!!))
        printToServer(jsonObject.toJSONString())
    }

    fun findUsers(login: String, count: Int) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_FIND_USER)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, login)
        jsonObject.put(JSONKeys.JSON_KEY_COUNT, count)
        printToServer(jsonObject.toJSONString())
    }

    fun phoneTo(login: String) {
        val jsonObject = createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_CALL)
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, mLogin)
        jsonObject.put(JSONKeys.JSON_KEY_COMPANION_LOGIN, login)
        printToServer(jsonObject.toJSONString())
    }

    fun answerToPhoneCall(successful: Boolean, thatLogin: String) {
        val jsonObject = JSONObject()
        jsonObject.put(JSONKeys.JSON_KEY_USER_LOGIN, mLogin)
        jsonObject.put(JSONKeys.JSON_KEY_COMPANION_LOGIN, thatLogin)
        if (successful) {
            jsonObject.put(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_SUCCESSFUL_CALL)
        } else {
            jsonObject.put(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_UNSUCCESSFUL_CALL)
        }
        printToServer(jsonObject.toJSONString())
    }

    fun close() {
        if (isCreated) {
            printToServer(createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_EXIT).toJSONString())
            resend!!.stopped = true
            input!!.close()
            output!!.close()
            socket!!.close()
        }
        isAuthorize = false
        isCreated = false
    }

    private inner class Resender : Thread() {

        var stopped: Boolean = false

        override fun run() {
            val jsonParser = JSONParser()
            while (!stopped) {
                try {
                    val s = input!!.readUTF()
                    if (s != null) {
                        val jsonObject = jsonParser.parse(s) as JSONObject
                        val responseType = jsonObject[JSONKeys.JSON_KEY_RESPONSE_TYPE] as String
                        when (responseType) {
                            ServerKeys.ON_SUCCESSFUL_CONNECT -> {
                                val modulus = BigInteger(jsonObject[JSONKeys.JSON_KEY_VALUE1] as String)
                                val pubExp = BigInteger(jsonObject[JSONKeys.JSON_KEY_VALUE1] as String)
                                publicKey = RSAEncryptionUtil.createPublicKey(modulus, pubExp)
                            }
                            ServerKeys.ON_SUCCESSFUL_LOGIN -> {
                                isAuthorize = true
                                socket!!.soTimeout = Constants.SOCKET_TIMEOUT2
                            }
                            ServerKeys.ON_CONNECTION_CHECK -> {
                                printToServer(createShortJSON(JSONKeys.JSON_KEY_RESPONSE_TYPE, ServerKeys.ON_CONNECTION_CHECK).toJSONString())
                            }
                            ServerKeys.ON_EXIT -> close()
                            ServerKeys.GET_FILE -> {
                                val name = jsonObject[JSONKeys.JSON_KEY_ATTACHMENT_NAME] as String
                                val length = (jsonObject[JSONKeys.JSON_KEY_ATTACHMENT_LENGTH] as String).toInt()
                                loadFile(createFilePath(name), length)
                            }
                        }
                        publicResponseQueue.add(jsonObject)
                        privateResponseQueue.add(jsonObject)
                        if (responseType != ServerKeys.ON_CONNECTION_CHECK) println("RESPONSE " + s)

                    }
                } catch (e: IOException) {
                    close()
                    break
                }
            }
        }
    }
}