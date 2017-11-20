package helfi2012.chat.services

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Binder
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.util.Log
import helfi2012.chat.R
import helfi2012.chat.activities.TextChatActivity
import helfi2012.chat.activities.UsersActivity
import helfi2012.chat.encryption.RSAEncryptionUtil
import helfi2012.chat.models.ChatMessage
import helfi2012.chat.models.UserInformation
import helfi2012.chat.tcpconnection.JSONKeys
import helfi2012.chat.tcpconnection.ServerKeys
import helfi2012.chat.tcpconnection.TCPClient
import helfi2012.chat.utils.Constants
import helfi2012.chat.utils.SettingsUtil
import helfi2012.chat.utils.Utils
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey

class ClientService : Service() {

    companion object {
        private val TAG = "ClientService"
    }

    private var client: TCPClient? = null
    private var userInformation: UserInformation? = null

    private var activities = ArrayList<Callbacks>()
    private val mBinder = LocalBinder()
    private var handler = Handler()
    var connected = false

    private var keyPair: KeyPair? = null

    private var connectingThread: ConnectingThread? = null

    private var serviceRunnable = object : Runnable {
        override fun run() {
            if (client != null && client!!.isCreated) {
                val jsonObject = client!!.response
                if (jsonObject != null) {
                    val responseType = jsonObject[JSONKeys.JSON_KEY_RESPONSE_TYPE] as String
                    when (responseType) {
                        ServerKeys.ON_SUCCESSFUL_CONNECT -> {
                            client!!.login(userInformation!!.login, userInformation!!.password)
                        }
                        ServerKeys.ON_SUCCESSFUL_LOGIN -> {
                            connected = true
                            activities.forEach { it.onConnectionStateChange(connected) }
                        }
                        ServerKeys.ON_FIND_USER -> {
                            val array = jsonObject[JSONKeys.JSON_KEY_USER_LIST] as JSONArray
                            val list: ArrayList<UserInformation> = ArrayList()
                            for (item in array) {
                                val obj = item as JSONObject
                                val user = UserInformation()
                                user.login = obj[JSONKeys.JSON_KEY_USER_LOGIN] as String
                                user.ipAddress = obj[JSONKeys.JSON_KEY_USER_IP_ADDRESS] as String
                                user.online = obj[JSONKeys.JSON_KEY_USER_ONLINE] as Boolean
                                val fileName = obj[JSONKeys.JSON_KEY_ATTACHMENT_NAME] as String
                                if (!fileName.isEmpty()) {
                                    user.iconPath = Environment.getExternalStorageDirectory().toString() +
                                            Constants.APP_PREFERENCES_DIRECTORY_BASE + fileName
                                    val file = File(user.iconPath)
                                    if (!file.exists()) {
                                        client!!.getFile(fileName)
                                    }
                                }
                                if (obj.containsKey(JSONKeys.JSON_KEY_MESSAGE_NAME)) {
                                    val name = obj[JSONKeys.JSON_KEY_MESSAGE_NAME] as String
                                    var text = obj[JSONKeys.JSON_KEY_MESSAGE_TEXT] as String
                                    val keyPair = SettingsUtil.loadRSAKeys(this@ClientService, user.login)
                                    text = if (keyPair != null) {
                                        RSAEncryptionUtil.decrypt(text, keyPair.private)
                                    } else {
                                        ""
                                    }
                                    user.lastMessage = ChatMessage(name, text, obj[JSONKeys.JSON_KEY_MESSAGE_TIME] as Long)
                                }
                                list.add(user)
                            }
                            activities.forEach { it.onUsersListReceive(list) }
                        }
                        ServerKeys.ON_CALL -> {
                            val user = UserInformation()
                            user.login = jsonObject[JSONKeys.JSON_KEY_USER_LOGIN] as String
                            user.ipAddress = jsonObject[JSONKeys.JSON_KEY_USER_IP_ADDRESS] as String

                            val fileName = jsonObject[JSONKeys.JSON_KEY_ATTACHMENT_NAME] as String
                            if (!fileName.isEmpty()) {
                                user.iconPath = Environment.getExternalStorageDirectory().toString() +
                                        Constants.APP_PREFERENCES_DIRECTORY_BASE + fileName
                                /*val file = File(user.iconPath)
                                if (!file.exists()) {
                                    client!!.getFile(fileName)
                                }*/
                            }
                            val isMy = client!!.mLogin == user.login
                            if (!isMy) {
                                val bitmap: Bitmap? = if (user.iconPath.isEmpty()) {
                                    BitmapFactory.decodeResource(applicationContext.resources, R.drawable.person_icon)
                                } else {
                                    BitmapFactory.decodeFile(user.iconPath)
                                }
                                Utils.createNotification(this@ClientService, Constants.APP_PREFERENCES_PHONE_CALL_NOTIFICATION_ID,
                                        getString(R.string.app_name), getString(R.string.income_call_label).plus(user.login), true,
                                        R.drawable.ic_notifications_black_24dp, bitmap,
                                        LongArray(20, { 1000 }), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                                        PendingIntent.getActivity(this@ClientService, 0,
                                                Intent(this@ClientService, UsersActivity::class.java)
                                                        .putExtra(Constants.APP_PREFERENCES_PHONE_CALL, user),
                                                PendingIntent.FLAG_ONE_SHOT))
                            }
                            if (activities.isEmpty()) {
                                val intent = Intent(this@ClientService, UsersActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.putExtra(Constants.APP_PREFERENCES_PHONE_CALL, user)
                                startActivity(intent)
                            } else {
                                activities.forEach { it.onPhoneCallReceive(isMy, user) }
                            }
                        }
                        ServerKeys.ON_SUCCESSFUL_CALL -> {
                            Utils.destroyNotification(this@ClientService, Constants.APP_PREFERENCES_PHONE_CALL_NOTIFICATION_ID)
                            activities.forEach { it.onPhoneCallAnswer(true) }
                        }
                        ServerKeys.ON_UNSUCCESSFUL_CALL -> {
                            Utils.destroyNotification(this@ClientService, Constants.APP_PREFERENCES_PHONE_CALL_NOTIFICATION_ID)
                            activities.forEach { it.onPhoneCallAnswer(false) }
                        }
                        ServerKeys.ON_CLIENT_MESSAGE -> {
                            val message = client!!.createMessageFormJSON(jsonObject)
                            message.isMy = message.name == client!!.mLogin
                            message.attachments.forEach { (name, path) ->
                                val file = File(path)
                                if (!file.exists()) {
                                    client!!.getFile(name)
                                }
                            }
                            val user = UserInformation()
                            user.login = jsonObject[JSONKeys.JSON_KEY_USER_LOGIN] as String
                            user.iconPath = jsonObject[JSONKeys.JSON_KEY_ATTACHMENT_NAME] as String
                            activities.forEach { it.onChatMessageReceive(message, false) }
                            for (activity in activities) {
                                if (activity::class.java != TextChatActivity::class.java && !message.isMy) {
                                    val bitmap: Bitmap? = if (user.iconPath.isEmpty()) {
                                        BitmapFactory.decodeResource(applicationContext.resources, R.drawable.person_icon)
                                    } else {
                                        BitmapFactory.decodeFile(user.iconPath)
                                    }
                                    Utils.createNotification(this@ClientService, Constants.APP_PREFERENCES_NEW_MESSAGE_NOTIFICATION_ID,
                                            getString(R.string.income_message_label).plus(message.name), message.text,
                                            true, R.drawable.ic_notifications_black_24dp,
                                            bitmap, null, null,
                                            PendingIntent.getActivity(this@ClientService, 0,
                                                    Intent(this@ClientService, TextChatActivity::class.java)
                                                            .putExtra(Constants.APP_PREFERENCES_USER_REQUEST, user),
                                                    PendingIntent.FLAG_ONE_SHOT))
                                }
                            }
                        }
                        ServerKeys.GET_DIALOG -> {
                            val messagesArray = jsonObject[JSONKeys.JSON_KEY_MESSAGE_LIST] as JSONArray
                            if (messagesArray.isEmpty()) {
                                activities.forEach { it.onChatMessageReceive(null, true) }
                            } else {
                                messagesArray.forEach { it ->
                                    val message = client!!.createMessageFormJSON(it as JSONObject)
                                    message.isMy = message.name == client!!.mLogin
                                    message.attachments.forEach { (name, path) ->
                                        val file = File(path)
                                        if (!file.exists()) {
                                            client!!.getFile(name)
                                        }
                                    }
                                    activities.forEach { it.onChatMessageReceive(message, true) }
                                }
                            }
                        }
                        ServerKeys.CREATE_DIALOG -> {
                            val login = jsonObject[JSONKeys.JSON_KEY_USER_LOGIN] as String
                            val first = jsonObject[JSONKeys.JSON_KEY_VALUE1] as Boolean
                            if (first) {
                                val modulus = BigInteger(jsonObject[JSONKeys.JSON_KEY_VALUE2] as String)
                                val publicExp = BigInteger(jsonObject[JSONKeys.JSON_KEY_VALUE3] as String)
                                val publicKey = RSAEncryptionUtil.createPublicKey(modulus, publicExp)

                                val keyPair = RSAEncryptionUtil.generateKeys()
                                SettingsUtil.saveRSAKeys(this@ClientService, login, keyPair.public, keyPair.private)

                                createDialog(login, publicKey, keyPair)

                                sendMessage(login, RSAEncryptionUtil.encrypt(getString(R.string.on_secret_chat_create).plus(login),
                                        keyPair.public), arrayOf())
                            } else {
                                val publicModulus = BigInteger(jsonObject[JSONKeys.JSON_KEY_VALUE2] as String)
                                val publicExp = BigInteger(jsonObject[JSONKeys.JSON_KEY_VALUE3] as String)
                                val privateModulus = BigInteger(
                                        RSAEncryptionUtil.decrypt(jsonObject[JSONKeys.JSON_KEY_VALUE4] as String, keyPair!!.private))
                                val privateExp = BigInteger(
                                        RSAEncryptionUtil.decrypt(jsonObject[JSONKeys.JSON_KEY_VALUE5] as String, keyPair!!.private))
                                val publicKey = RSAEncryptionUtil.createPublicKey(publicModulus, publicExp)
                                val privateKey = RSAEncryptionUtil.createPrivateKey(privateModulus, privateExp)
                                SettingsUtil.saveRSAKeys(this@ClientService, login, publicKey, privateKey)
                            }
                        }
                    }
                    activities.forEach { it.onJSONReceive(jsonObject) }
                }
            } else {
                connected = false
                activities.forEach { it.onConnectionStateChange(connected) }
            }
            handler.postDelayed(this, Constants.TIMER_TICK)
        }
    }

    private inner class ConnectingThread: Thread() {
        override fun run() {
            while (!Thread.currentThread().isInterrupted) {
                if (client == null || !client!!.isCreated) {
                    client = TCPClient(Constants.TCP_PORT, Constants.SERVER_HOST)
                    client!!.externalStorageDirectory = Environment.getExternalStorageDirectory().toString()
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        val serviceInstance: ClientService
            get() = this@ClientService
    }

    interface Callbacks {
        fun onJSONReceive(jsonObject: JSONObject)
        fun onConnectionStateChange(connected: Boolean)
        fun onPhoneCallReceive(isMy: Boolean, userInformation: UserInformation)
        fun onUsersListReceive(users: ArrayList<UserInformation>)
        fun onPhoneCallAnswer(result: Boolean)
        fun onChatMessageReceive(chatMessage: ChatMessage?, isDialog: Boolean)
    }

    /* public functions */

    fun addActivity(activity: Activity) {
        Log.d(TAG, "register new Activity")
        activities.add(activity as Callbacks)
        activity.onConnectionStateChange(connected)
    }

    fun removeActivity(activity: Activity) {
        Log.d(TAG, "remove Activity")
        activities.remove(activity as Callbacks)
    }

    fun runClient(userInformation: UserInformation) {
        Log.d(TAG, "runClient")
        this.userInformation = userInformation
        connectingThread = ConnectingThread()
        connectingThread!!.start()
        handler.postDelayed(serviceRunnable, 0)
    }

    fun stopClient() {
        Log.d(TAG, "stopClient")
        connectingThread!!.interrupt()
        connectingThread = null
        handler.removeCallbacks(serviceRunnable)
        if (client != null) {
            client!!.close()
        }
    }

    fun makePhoneCall(login: String) = client!!.phoneTo(login)

    fun answerToPhoneCall(result: Boolean, login: String) =
            client!!.answerToPhoneCall(result, login)

    fun findUser(login: String, count: Int) = client!!.findUsers(login, count)

    fun changeAvatar(login: String, path: String) = client!!.changeAvatar(login, path)

    fun sendMessage(name: String, text: String, paths: Array<String>) =
            client!!.sendMessage(name, text, paths)

    fun getDialog(login: String, index: Int, count: Int) = client!!.getDialog(login, index, count)

    fun getDialogs() = client!!.getDialogs()

    fun createDialog(login: String, keyPair: KeyPair) {
        this.keyPair = keyPair
        client!!.createDialog(login, keyPair.public)
    }

    fun createDialog(login: String, encryptionKey: PublicKey, keyPair: KeyPair) =
            client!!.createDialog(login, encryptionKey, keyPair)

    fun deleteMessages(login: String, startTime: Long, count: Int) =
            client!!.deleteMessages(login, startTime, count)

    /* Service lifecycle */

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onBind(arg0: Intent): IBinder? {
        Log.d(TAG, "onBind")
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int = Service.START_NOT_STICKY
}