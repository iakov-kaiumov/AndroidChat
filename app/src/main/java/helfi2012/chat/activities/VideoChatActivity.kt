package helfi2012.chat.activities

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.widget.*
import com.github.florent37.viewanimator.ViewAnimator
import helfi2012.chat.R
import helfi2012.chat.fragments.CameraFragment
import helfi2012.chat.models.ChatMessage
import helfi2012.chat.models.UserInformation
import helfi2012.chat.audio.AudioPlayer
import helfi2012.chat.audio.AudioRecorder
import helfi2012.chat.services.ClientService
import helfi2012.chat.udpconnection.UDPClient
import helfi2012.chat.utils.Constants
import org.json.simple.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*


class VideoChatActivity : AppCompatActivity(), ClientService.Callbacks {

    private var timer = Timer()
    private var mUDPClient: UDPClient? = null
    private var thatUser: UserInformation? = null
    private var clientService: ClientService? = null
    private var serviceIntent: Intent? = null
    private var mBind = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioButton: Button? = null
    private var videoButton: Button? = null
    private var foreignImageView: ImageView? = null
    private var cameraFragment: CameraFragment? = null
    private var textView: TextView? = null
    private var progressBar: ProgressBar? = null
    private var audioRecorder: AudioRecorder? = null
    private val audioPlayer = AudioPlayer()
    private var videoActive = false
    private var audioActive = false
    private var frontCamera = true
    private var pictureQuality = 30

    private fun askPermissions() =
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    Constants.APP_PREFERENCES_PERMISSION_REQUEST)

    private fun initializeViews() {
        supportActionBar!!.hide()

        val stopButton = findViewById(R.id.stop_button)
        stopButton.setOnClickListener { onBackPressed() }

        audioButton = findViewById(R.id.audio_button) as Button
        audioButton!!.setOnClickListener {
            audioActive = !audioActive
            if (audioActive) {
                audioRecorder!!.start(mUDPClient!!)
                audioButton!!.background = getDrawable(android.R.drawable.presence_audio_online)
            } else {
                audioRecorder!!.stop()
                audioButton!!.background = getDrawable(android.R.drawable.presence_audio_busy)
            }
        }

        videoButton = findViewById(R.id.video_button) as Button
        videoButton!!.setOnClickListener {
            videoActive = !videoActive
            if (videoActive) {
                cameraFragment!!.createPreview(frontCamera)
                videoButton!!.background = getDrawable(android.R.drawable.presence_video_online)
                timer = Timer()
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        val outputStream = ByteArrayOutputStream()
                        if (cameraFragment != null && cameraFragment!!.textureViewBitmap != null) {
                            cameraFragment!!.textureViewBitmap.compress(Bitmap.CompressFormat.JPEG, pictureQuality, outputStream)
                            mUDPClient!!.sendByteArray(outputStream.toByteArray(), UDPClient.VIDEO_STATE)
                        }
                    }
                }, Constants.TIMER_DELAY, Constants.TIMER_TICK)
            } else {
                timer.cancel()
                cameraFragment!!.closePreview()
                videoButton!!.background = getDrawable(android.R.drawable.presence_video_busy)
            }
        }

        val changeCameraButton = findViewById(R.id.change_camera_button)
        changeCameraButton.setOnClickListener {
            if (videoActive) {
                ViewAnimator
                        .animate(changeCameraButton)
                        .rotation(360f)
                        .duration(400)
                        .start()
                ViewAnimator
                        .animate(cameraFragment!!.textureView)
                        .flipVertical()
                        .duration(400)
                        .start()
                frontCamera = !frontCamera
                cameraFragment!!.closePreview()
                cameraFragment!!.createPreview(frontCamera)
            }
        }

        textView = findViewById(R.id.text_view) as TextView

        foreignImageView = findViewById(R.id.image_view) as ImageView

        cameraFragment = CameraFragment.newInstance()
        fragmentManager.beginTransaction()
                .replace(R.id.fragment, cameraFragment)
                .commit()

        progressBar = findViewById(R.id.progress_bar) as ProgressBar
    }

    /* Activity lifecycle */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_chat)
        askPermissions()
        initializeViews()
        thatUser = intent.getSerializableExtra(Constants.APP_PREFERENCES_USER_REQUEST) as UserInformation
        if (thatUser!!.iconPath.isEmpty()) {
            foreignImageView!!.setImageResource(R.drawable.person_icon)
        } else {
            foreignImageView!!.setImageBitmap(BitmapFactory.decodeFile(thatUser!!.iconPath))
        }
        serviceIntent = Intent(this@VideoChatActivity, ClientService::class.java)
        if (!mBind) {
            mBind = bindService(serviceIntent, mConnection, BIND_AUTO_CREATE)
        }

        audioRecorder = AudioRecorder()
        audioPlayer.play()

        mUDPClient = UDPClient(Constants.UDP_PORT, thatUser!!.ipAddress)
        mUDPClient!!.onAudioReceive = { bytes: ByteArray ->
            audioPlayer.write(bytes)
        }

        mUDPClient!!.onImageReceive = { bytes: ByteArray ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            runOnUiThread { foreignImageView!!.setImageBitmap(bitmap) }
        }

        mUDPClient!!.receiveThread.start()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, localClassName)
        wakeLock!!.acquire()

        audioButton!!.callOnClick()
    }

    override fun onDestroy() {
        wakeLock!!.release()
        stop()
        super.onDestroy()
    }

    override fun onBackPressed() {
        stop()
        finish()
    }

    private fun stop() {
        timer.cancel()
        if (cameraFragment != null) cameraFragment!!.closePreview()

        audioPlayer.stop()
        audioRecorder!!.stop()
        if (mUDPClient != null) {
            mUDPClient!!.close()
            mUDPClient!!.receiveThread.interrupt()
        }
        if (mBind) {
            clientService!!.removeActivity(this)
            unbindService(mConnection)
            mBind = false
        }
    }

    /* Connection with Service */

    private val mConnection = object: ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ClientService.LocalBinder
            clientService = binder.serviceInstance
            clientService!!.addActivity(this@VideoChatActivity)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBind = false
        }
    }

    /* ClientService.CallBack override functions */

    override fun onJSONReceive(jsonObject: JSONObject) = Unit

    override fun onConnectionStateChange(connected: Boolean) = if (connected) {
        progressBar!!.visibility = ProgressBar.GONE
    } else {
        progressBar!!.visibility = ProgressBar.VISIBLE
        onBackPressed()
    }

    override fun onPhoneCallReceive(isMy: Boolean, userInformation: UserInformation) = Unit

    override fun onPhoneCallAnswer(result: Boolean) {
        if (!result)
            onBackPressed()
    }

    override fun onUsersListReceive(users: ArrayList<UserInformation>) = Unit

    override fun onChatMessageReceive(chatMessage: ChatMessage?, isDialog: Boolean) = Unit
}