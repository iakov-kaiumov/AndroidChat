package helfi2012.chat.activities

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.support.constraint.ConstraintLayout
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Size
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import de.hdodenhof.circleimageview.CircleImageView
import helfi2012.chat.R
import helfi2012.chat.adapters.ChatAdapter
import helfi2012.chat.encryption.RSAEncryptionUtil
import helfi2012.chat.models.ChatMessage
import helfi2012.chat.models.UserInformation
import helfi2012.chat.services.ClientService
import helfi2012.chat.tcpconnection.JSONKeys
import helfi2012.chat.tcpconnection.ServerKeys
import helfi2012.chat.tcpconnection.TCPClient
import helfi2012.chat.utils.Constants
import helfi2012.chat.utils.SettingsUtil
import helfi2012.chat.utils.Utils
import org.json.simple.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPair
import java.util.*

class TextChatActivity : AppCompatActivity(), ClientService.Callbacks {

    companion object {
        private object SCROLL_TYPE {
            val SCROLL_TO_TOP = 0
            val SCROLL_TO_BOTTOM = 1
            val SCROLL_DISABLED = 2
        }
    }

    private var clientService: ClientService? = null
    private var serviceIntent: Intent? = null
    private var mBind = false
    private var screenSize = Size(0,0)

    private var sendButton: Button? = null
    private var attachLayout: LinearLayout? = null
    private var photoLayout: LinearLayout? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var textInput: EditText? = null
    private var progressBar: ProgressBar? = null
    private var appBarIcon: CircleImageView? = null
    private var appBarTitle: TextView? = null
    private var appBarSubtitle: TextView? = null
    private var listView: ListView? = null
    private var adapter: ChatAdapter? = null
    private val attachPaths = ArrayList<String>()

    private var thatUser: UserInformation? = null
    private var messageIndex = 0
    private val messageOffset = 10
    private var firstReceive = true

    private var keyPair: KeyPair? = null

    private fun initializeScreenSize() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenSize = Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    private fun setUpActionBar() {
        val appbar = (getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).
                inflate(R.layout.activity_text_chat_app_bar, null)
        appBarTitle = appbar.findViewById(R.id.title) as TextView
        appBarSubtitle = appbar.findViewById(R.id.subtitle) as TextView
        appBarIcon = appbar.findViewById(R.id.icon) as CircleImageView
        appBarIcon!!.setOnClickListener {
            if (thatUser != null && thatUser!!.iconPath.isNotEmpty()) {
                startActivity(Intent(this, ImageActivity::class.java).
                        putExtra(Constants.APP_PREFERENCES_FULLSCREEN_IMAGE, thatUser!!.iconPath))
            }
        }
        if (thatUser!!.iconPath.isEmpty()) {
            appBarIcon!!.setImageResource(R.drawable.person_icon)
        } else {
            val width = appBarIcon!!.layoutParams.width
            var bitmap = BitmapFactory.decodeFile(thatUser!!.iconPath)
            if (bitmap != null) {
                bitmap = Bitmap.createScaledBitmap(bitmap, width,
                        (width * bitmap.height.toDouble() / bitmap.width.toDouble()).toInt(),
                        false)
                appBarIcon!!.setImageBitmap(bitmap)
            }
        }
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowCustomEnabled(true)
        supportActionBar!!.setCustomView(appbar,
                android.support.v7.app.ActionBar.LayoutParams(android.support.v7.app.ActionBar.LayoutParams.MATCH_PARENT,
                        android.support.v7.app.ActionBar.LayoutParams.MATCH_PARENT))
    }

    private fun initializeViews() {
        setUpActionBar()

        val takePhotoButton = findViewById(R.id.text_chat_take_photo_button)
        takePhotoButton.setOnClickListener {
            captureImage()
            attachLayout!!.visibility = LinearLayout.GONE
        }

        val attachButton = findViewById(R.id.text_chat_attach_button)
        attachButton.setOnClickListener {
            if (attachLayout!!.visibility == LinearLayout.VISIBLE) {
                attachLayout!!.visibility = LinearLayout.GONE
            } else {
                attachLayout!!.visibility = LinearLayout.VISIBLE
            }
        }

        val openGalleryButton = findViewById(R.id.text_chat_gallery_button)
        openGalleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = Constants.APP_PREFERENCES_INTENT_GALLERY_TYPE
            startActivityForResult(intent, Constants.APP_PREFERENCES_REQUEST_CODE_GALLERY_PHOTO)
            attachLayout!!.visibility = LinearLayout.INVISIBLE
        }

        sendButton = findViewById(R.id.text_chat_send_button) as Button
        sendButton!!.setOnClickListener {
            val attachLoaded = (0 until attachPaths.size).none { photoLayout!!.getChildAt(it).findViewById(R.id.custom_progressbar).
                    visibility != ProgressBar.GONE }
            if (attachLoaded && (!textInput!!.text.toString().isEmpty() || attachPaths.size != 0) && clientService != null) {
                progressBar!!.visibility = ProgressBar.VISIBLE
                progressBar!!.bringToFront()
                photoLayout!!.removeAllViews()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).
                        hideSoftInputFromWindow(sendButton!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                clientService!!.sendMessage(thatUser!!.login,
                        RSAEncryptionUtil.encrypt(textInput!!.text.toString(), keyPair!!.public),
                        attachPaths.toTypedArray())
                textInput!!.text.clear()
                attachPaths.clear()
            }
        }

        attachLayout = findViewById(R.id.text_chat_attach_layout) as LinearLayout
        attachLayout!!.visibility = LinearLayout.GONE

        photoLayout = findViewById(R.id.text_chat_photo_layout) as LinearLayout

        textInput = findViewById(R.id.text_chat_edit_text) as EditText

        progressBar = findViewById(R.id.text_chat_progress_bar) as ProgressBar

        listView = findViewById(R.id.text_chat_list_view) as ListView
        listView!!.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                if (listView!!.getChildAt(0) != null) {
                    swipeRefreshLayout!!.isEnabled = listView!!.firstVisiblePosition == 0
                            && listView!!.getChildAt(0).top == 0
                }
            }
        })
        listView!!.setOnItemLongClickListener { _, _, position, _ ->
            val builder = AlertDialog.Builder(this@TextChatActivity)
            builder.setItems(arrayOf(getString(R.string.copy_text), getString(R.string.delete_label)))
            { dialog, which ->
                val message = adapter!!.chatMessages[position]
                when (which) {
                    0 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("", message.text)
                        clipboard.primaryClip = clip
                        Toast.makeText(this, getString(R.string.copy_successful), Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        if (clientService!!.connected) {
                            clientService!!.deleteMessages(thatUser!!.login, message.time, 1)
                            adapter!!.chatMessages.removeAt(position)
                            updateAdapter(SCROLL_TYPE.SCROLL_DISABLED)
                        }
                    }
                }
                dialog.cancel()
            }
            builder.show()
            true
        }
        adapter = ChatAdapter(this@TextChatActivity)
        listView!!.adapter = adapter

        swipeRefreshLayout = findViewById(R.id.text_chat_refresh_layout) as SwipeRefreshLayout
        swipeRefreshLayout!!.setOnRefreshListener {
            if (clientService != null && clientService!!.connected) {
                clientService!!.getDialog(thatUser!!.login, messageIndex, messageOffset)
                swipeRefreshLayout!!.isRefreshing = true
            } else {
                swipeRefreshLayout!!.isRefreshing = false
            }
        }
    }

    private fun captureImage() {
        attachPaths.add(Environment.getExternalStorageDirectory().toString() + Constants.APP_PREFERENCES_DIRECTORY_BASE +
                System.currentTimeMillis() +".jpeg")
        val file = File(attachPaths.last())
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file))
        startActivityForResult(takePictureIntent, Constants.APP_PREFERENCES_REQUEST_CODE_CAMERA_PHOTO)
    }

    private fun createDirectory() {
        val file = File(Environment.getExternalStorageDirectory().toString() + Constants.APP_PREFERENCES_DIRECTORY_BASE)
        if (!file.exists()) {
            file.mkdir()
        }
    }

    private fun addPhoto(bitmap: Bitmap) {
        class ViewHolder(parentView: View) {
            var content: LinearLayout? = null
            var contentWithBG: ConstraintLayout? = null
            var imageView: ImageView? = null
            var closeButton: Button? = null
            var progressBar: ProgressBar? = null
            init {
                content = parentView.findViewById(R.id.attachment_image_layout) as LinearLayout
                contentWithBG = parentView.findViewById(R.id.custom_relative_layout) as ConstraintLayout
                imageView = parentView.findViewById(R.id.custom_image_view) as ImageView
                closeButton = parentView.findViewById(R.id.custom_close_button) as Button
                progressBar = parentView.findViewById(R.id.custom_progressbar) as ProgressBar
            }
        }

        val path = attachPaths[attachPaths.size - 1]

        val vi = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val convertView = vi.inflate(R.layout.attachment_layout, null)
        val holder = ViewHolder(convertView)
        holder.closeButton!!.tag = path
        val width = resources.getDimension(R.dimen.attachment_width).toInt()
        holder.imageView!!.setImageBitmap(Bitmap.createScaledBitmap(bitmap,
                width * bitmap.width / bitmap.height, width, false))
        holder.content!!.setPadding(16, 16, 16, 16)
        holder.progressBar!!.startAnimation(AnimationUtils.loadAnimation(this, R.anim.progress_bar_rotate))
        photoLayout!!.addView(convertView)

        Thread({
            val fileOutputStream = FileOutputStream(path)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            val client = TCPClient(Constants.TCP_PORT, Constants.SERVER_HOST)
            client.externalStorageDirectory = Environment.getExternalStorageDirectory().toString()
            holder.closeButton!!.setOnClickListener{
                photoLayout!!.removeViewAt(attachPaths.indexOf(path))
                attachPaths.remove(path)
                client.close()
            }

            client.sendAttachment(path, {
                runOnUiThread {
                    try {
                        photoLayout!!.removeViewAt(attachPaths.indexOf(path))
                    } catch (e: Exception) {}
                    attachPaths.remove(path)
                    Utils.createAlertDialog(this@TextChatActivity, getString(R.string.error_title), getString(R.string.error_network),
                            getString(R.string.ok_title), {}, getString(R.string.cancel_title), {})
                }
            },
            { progress: Int ->
                val index = attachPaths.indexOf(path)
                val progressBar = photoLayout!!.getChildAt(index).findViewById(R.id.custom_progressbar) as ProgressBar
                println(index.toString() + ", " + progress.toString())
                progressBar.progress = progress
            },
            {
                val index = attachPaths.indexOf(path)
                val progressBar = photoLayout!!.getChildAt(index).findViewById(R.id.custom_progressbar) as ProgressBar
                runOnUiThread {
                    progressBar.clearAnimation()
                    progressBar.visibility = ProgressBar.GONE
                    holder.closeButton!!.visibility = Button.GONE
                }
            })
        }).start()
    }

    private fun updateAdapter(scrollType: Int) {
        adapter!!.notifyDataSetChanged()
        when (scrollType) {
            SCROLL_TYPE.SCROLL_TO_TOP -> listView!!.smoothScrollToPosition(0)
            SCROLL_TYPE.SCROLL_TO_BOTTOM -> listView!!.smoothScrollToPosition(listView!!.count - 1)
        }
    }

    /* Menu */

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_text_chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            android.R.id.home -> onBackPressed()
            R.id.menu_delete_chat -> {
                if (clientService!!.connected) clientService!!.deleteMessages(thatUser!!.login, -1, -1)
                onBackPressed()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /* Activity lifecycle */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_chat)
        thatUser = intent.getSerializableExtra(Constants.APP_PREFERENCES_USER_REQUEST) as UserInformation
        createDirectory()
        initializeViews()
        keyPair = SettingsUtil.loadRSAKeys(this@TextChatActivity, thatUser!!.login)

        serviceIntent = Intent(this@TextChatActivity, ClientService::class.java)
        if (!mBind) {
            mBind = bindService(serviceIntent, mConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBind) {
            clientService!!.removeActivity(this)
            unbindService(mConnection)
            mBind = false
        }
    }

    override fun onResume() {
        initializeScreenSize()
        adapter!!.screenSize = screenSize
        super.onResume()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Constants.APP_PREFERENCES_REQUEST_CODE_CAMERA_PHOTO) {
            if (resultCode == RESULT_OK) {
                val file = File(attachPaths[attachPaths.size - 1])
                if (file.exists()) {
                    addPhoto(BitmapFactory.decodeFile(file.absolutePath))
                }
            }
        } else if (requestCode == Constants.APP_PREFERENCES_REQUEST_CODE_GALLERY_PHOTO) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    attachPaths.add(data.data.path)
                    addPhoto(BitmapFactory.decodeStream(contentResolver.openInputStream(data.data)))
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() = finish()

    /* Connection with Service */

    private val mConnection = object: ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ClientService.LocalBinder
            clientService = binder.serviceInstance
            clientService!!.addActivity(this@TextChatActivity)
            if (clientService!!.connected) {
                clientService!!.getDialog(thatUser!!.login, messageIndex, messageOffset)
                swipeRefreshLayout!!.isRefreshing = true
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBind = false
        }
    }

    /* ClientService.CallBack override functions */

    override fun onJSONReceive(jsonObject: JSONObject) {
        val responseType = jsonObject[JSONKeys.JSON_KEY_RESPONSE_TYPE] as String
        when (responseType) {
            ServerKeys.DELETE_MESSAGES -> {}
        }
    }

    override fun onConnectionStateChange(connected: Boolean) = if (connected) {
        appBarTitle!!.text = thatUser!!.login
        if (thatUser!!.online) {
            appBarSubtitle!!.text = getString(R.string.online_label)
        } else {
            appBarSubtitle!!.text = getString(R.string.offline_label)
        }
    } else {
        appBarTitle!!.text = getString(R.string.connecting_label)
        appBarSubtitle!!.text = ""
    }

    override fun onPhoneCallReceive(isMy: Boolean, userInformation: UserInformation) = Unit

    override fun onPhoneCallAnswer(result: Boolean) = Unit

    override fun onUsersListReceive(users: ArrayList<UserInformation>) = Unit

    override fun onChatMessageReceive(chatMessage: ChatMessage?, isDialog: Boolean) {
        if (chatMessage != null && keyPair != null) chatMessage.text = RSAEncryptionUtil.decrypt(chatMessage.text, keyPair!!.private)
        if (isDialog) {
            if (chatMessage != null) {
                adapter!!.chatMessages.add(0, chatMessage)
                messageIndex++
            }
            swipeRefreshLayout!!.isRefreshing = false
            if (firstReceive) {
                firstReceive = false
                updateAdapter(SCROLL_TYPE.SCROLL_TO_BOTTOM)
            } else {
                updateAdapter(SCROLL_TYPE.SCROLL_TO_TOP)
            }
        } else
        if (chatMessage != null) {
            messageIndex++
            adapter!!.chatMessages.add(chatMessage)
            progressBar!!.visibility = ProgressBar.GONE
            updateAdapter(SCROLL_TYPE.SCROLL_TO_BOTTOM)
        }
    }
}
