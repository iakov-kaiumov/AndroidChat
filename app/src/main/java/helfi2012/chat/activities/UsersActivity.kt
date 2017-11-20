package helfi2012.chat.activities

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.baoyz.swipemenulistview.SwipeMenuItem
import com.baoyz.swipemenulistview.SwipeMenuListView
import de.hdodenhof.circleimageview.CircleImageView
import helfi2012.chat.R
import helfi2012.chat.adapters.UsersAdapter
import helfi2012.chat.encryption.RSAEncryptionUtil
import helfi2012.chat.models.ChatMessage
import helfi2012.chat.models.UserInformation
import helfi2012.chat.services.ClientService
import helfi2012.chat.tcpconnection.JSONKeys
import helfi2012.chat.tcpconnection.ServerKeys
import helfi2012.chat.utils.Constants
import helfi2012.chat.utils.SettingsUtil
import helfi2012.chat.utils.Utils
import org.json.simple.JSONObject

class UsersActivity : AppCompatActivity(), ClientService.Callbacks, NavigationView.OnNavigationItemSelectedListener {

    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var listView: SwipeMenuListView? = null
    private var adapter: UsersAdapter? = null
    private var progressBar: ProgressBar? = null
    private var callingLayout: LinearLayout? = null
    private var phoneCallOkButton: FloatingActionButton? = null
    private var phoneCallCancelButton: FloatingActionButton? = null
    private var phoneCallTextView: TextView? = null
    private var phoneCallImageView: CircleImageView? = null
    private var navImageView: CircleImageView? = null
    private var navTitle: TextView? = null
    private var navSubtitle: TextView? = null
    private var thatUser = UserInformation()
    private var thisUser = UserInformation()
    private var isLoading = false

    private var clientService: ClientService? = null
    private var serviceIntent: Intent? = null
    private var mBind = false
    private val blockList = ArrayList<String>()

    companion object {
        private object CALL_STATE {
            val DEACTIVATE = 0
            val INCOMING = 1
            val OUTGOING_CONNECTED = 2
            val OUTGOING_DISCONNECTED = 3
        }
    }

    /* Connection with Service */
    private val mConnection = object: ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ClientService.LocalBinder
            clientService = binder.serviceInstance
            clientService!!.addActivity(this@UsersActivity)
            if (!clientService!!.connected) {
                clientService!!.runClient(thisUser)
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBind = false
        }
    }

    private fun setPolice() {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            builder.detectFileUriExposure()
        }
    }

    private fun loadUser() {
        val user = SettingsUtil.loadUserInformation(this)
        if (user.login.isEmpty()) {
            startActivityForResult(Intent(this, LoginActivity::class.java), Constants.APP_PREFERENCES_LOGIN_ACTIVITY_REQUEST)
        } else {
            thisUser = user
            navTitle!!.text = thisUser.login
            if (!mBind) {
                mBind = bindService(serviceIntent, mConnection, BIND_AUTO_CREATE)
            }
        }
    }

    /* view */

    private fun loadAvatar() {
        navImageView!!.setImageResource(R.drawable.person_icon)
        val path = SettingsUtil.loadAvatarPath(this)
        if (path.isNotEmpty()) {
            var bitmap = BitmapFactory.decodeFile(path)?: return
            val width = navImageView!!.layoutParams.width
            bitmap = Bitmap.createScaledBitmap(bitmap, width,
                    (width * bitmap.height.toDouble() / bitmap.width.toDouble()).toInt(),
                    false)
            navImageView!!.setImageBitmap(bitmap)
        }
    }

    private fun initializeViews() {
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        val toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.open_label, R.string.close_label)
        @Suppress("DEPRECATION")
        drawer.setDrawerListener(toggle)
        toggle.syncState()
        val navigationView = findViewById(R.id.nav_view) as NavigationView
        navigationView.setNavigationItemSelectedListener(this)
        navigationView.setCheckedItem(R.id.nav_dialogs)

        val headerView = navigationView.getHeaderView(0)
        navTitle = headerView.findViewById(R.id.nav_title) as TextView
        navSubtitle = headerView.findViewById(R.id.nav_subtitle) as TextView
        navSubtitle!!.text = getString(R.string.connecting_label)

        /* Avatar imageView */
        navImageView = headerView.findViewById(R.id.nav_image_view) as CircleImageView
        navImageView!!.setOnClickListener {
            val builder = AlertDialog.Builder(this@UsersActivity)
            builder.setTitle(getString(R.string.avatar_change_label))
            builder.setItems(arrayOf(getString(R.string.take_photo_label), getString(R.string.gallery_label), getString(R.string.open_label),
                    getString(R.string.delete_label))) { dialog, which ->
                val intent = Intent(this@UsersActivity, ImageActivity::class.java)
                intent.putExtra(Constants.APP_PREFERENCES_USER_REQUEST, thisUser.login)
                when (which) {
                    0 -> {
                        intent.type = Constants.APP_PREFERENCES_INTENT_CAPTURE_TYPE
                        startActivityForResult(intent, Constants.APP_PREFERENCES_CHANGE_AVATAR_REQUEST)
                    }
                    1 -> {
                        intent.type = Constants.APP_PREFERENCES_INTENT_GALLERY_TYPE
                        startActivityForResult(intent, Constants.APP_PREFERENCES_CHANGE_AVATAR_REQUEST)
                    }
                    2 -> {
                        val path = SettingsUtil.loadAvatarPath(this)
                        if (path.isNotEmpty()) {
                            startActivity(Intent(this, ImageActivity::class.java).
                                    putExtra(Constants.APP_PREFERENCES_FULLSCREEN_IMAGE, path))
                        }
                    }
                    3 -> clientService!!.changeAvatar(thisUser.login, "")
                }
                dialog.cancel()
            }
            builder.show()
        }

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout) as SwipeRefreshLayout
        swipeRefreshLayout!!.setOnRefreshListener {
            if (clientService!!.connected) {
                if (!isLoading) {
                    clientService!!.getDialogs()
                    isLoading = true
                }
                swipeRefreshLayout!!.isRefreshing = isLoading
            } else {
                swipeRefreshLayout!!.isRefreshing = false
            }
        }
        /* Users adapter & ListView */

        adapter = UsersAdapter(this)

        listView = findViewById(R.id.list_view) as SwipeMenuListView
        listView!!.adapter = adapter
        listView!!.emptyView = findViewById(R.id.textView)
        listView!!.setSwipeDirection(SwipeMenuListView.DIRECTION_LEFT)
        listView!!.setOnItemClickListener { _, _, position, _ ->
            val user = adapter!!.getItem(position)
            if (user.lastMessage == null) {
                Toast.makeText(this, getString(R.string.on_answer_wait), Toast.LENGTH_SHORT).show()
                if (!blockList.contains(user.login)) {
                    val keyPair = RSAEncryptionUtil.generateKeys()
                    clientService!!.createDialog(user.login, keyPair)
                    blockList.add(user.login)
                }
            } else {
                startActivity(Intent(this, TextChatActivity::class.java).
                        putExtra(Constants.APP_PREFERENCES_USER_REQUEST, user))
            }
        }
        listView!!.setOnItemLongClickListener { _, _, position, _ ->
            val user = adapter!!.getItem(position)
            val builder = AlertDialog.Builder(this@UsersActivity)
            builder.setItems(arrayOf(getString(R.string.delete_label)))
            { dialog, which ->
                if (which == 0) {
                    if (clientService!!.connected) {
                        clientService!!.deleteMessages(user.login, -1, -1)
                        clientService!!.getDialogs()
                    }
                }
                dialog.cancel()
            }
            builder.show()
            true
        }
        listView!!.setOnMenuItemClickListener { position, _, _ ->
            val user = adapter!!.getItem(position)
            if (!clientService!!.connected) {
                Utils.createAlertDialog(this@UsersActivity, getString(R.string.error_title), getString(R.string.error_network),
                        getString(R.string.ok_title), {}, getString(R.string.cancel_title), {})
            } else
            if (user.lastMessage == null) {

            } else
            if (!user.online) {
                Toast.makeText(this, getString(R.string.user_offline), Toast.LENGTH_SHORT).show()
            } else
            if (adapter!!.getItem(position).login == thisUser.login) {
                startActivityForResult(Intent(this@UsersActivity, VideoChatActivity::class.java).
                        putExtra(Constants.APP_PREFERENCES_USER_REQUEST, UserInformation()),
                        Constants.APP_PREFERENCES_CHATTING_ACTIVITY_REQUEST)
            } else {
                thatUser = adapter!!.getItem(position)
                clientService!!.makePhoneCall(thatUser.login)
                onChangeCallState(CALL_STATE.OUTGOING_DISCONNECTED)
            }
            false
        }
        listView!!.setMenuCreator { menu ->
            val menuItem = SwipeMenuItem(applicationContext)
            menuItem.background = ColorDrawable(ContextCompat.getColor(this, R.color.colorGreen))
            menuItem.width = resources.getDimension(R.dimen.list_view_menu_item_width).toInt()
            @Suppress("DEPRECATION")
            menuItem.icon = getDrawable(android.R.drawable.stat_sys_phone_call)
            menu.addMenuItem(menuItem)
        }

        callingLayout = findViewById(R.id.calling_layout) as LinearLayout

        phoneCallOkButton = findViewById(R.id.phone_call_ok_button) as FloatingActionButton
        phoneCallOkButton!!.setOnClickListener {
            if (clientService!!.connected) clientService!!.answerToPhoneCall(true, thatUser.login)
        }

        phoneCallCancelButton = findViewById(R.id.phone_call_cancel_button) as FloatingActionButton
        phoneCallCancelButton!!.setOnClickListener {
            if (clientService!!.connected) clientService!!.answerToPhoneCall(false, thatUser.login)
        }

        phoneCallImageView = findViewById(R.id.phone_call_image_view) as CircleImageView
        phoneCallTextView = findViewById(R.id.phone_call_label) as TextView

        progressBar = findViewById(R.id.progress_bar) as ProgressBar
        progressBar!!.visibility = ProgressBar.VISIBLE
    }

    private fun updateAdapter() = adapter!!.notifyDataSetChanged()

    private fun onChangeCallState(state: Int) {
        if (state == CALL_STATE.DEACTIVATE) {
            listView!!.visibility = ListView.VISIBLE
            callingLayout!!.visibility = LinearLayout.GONE
            phoneCallOkButton!!.visibility = Button.GONE
        } else {
            listView!!.visibility = ListView.GONE
            callingLayout!!.visibility = LinearLayout.VISIBLE
            if (thatUser.iconPath.isEmpty()) {
                //phoneCallImageView!!.setImageResource(R.drawable.person_icon)
            } else {
                //phoneCallImageView!!.setImageDrawable(Drawable.createFromPath(thatUser.iconPath))
            }
            when (state) {
                CALL_STATE.INCOMING -> {
                    phoneCallTextView!!.text = getString(R.string.income_call_label).plus(thatUser.login)
                    phoneCallOkButton!!.visibility = Button.VISIBLE
                }
                CALL_STATE.OUTGOING_CONNECTED -> {
                    phoneCallTextView!!.text = getString(R.string.outcome_call_label).plus(thatUser.login)
                }
                CALL_STATE.OUTGOING_DISCONNECTED -> {
                    phoneCallTextView!!.text = getString(R.string.connecting_label)
                }
            }
        }
    }

    /* Activity lifecycle */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users)
        initializeViews()
        setPolice()
        serviceIntent = Intent(this@UsersActivity, ClientService::class.java)
        if (!Utils.isMyServiceRunning(this, ClientService::class.java)) {
            startService(serviceIntent)
        }
        loadUser()
        if (intent.hasExtra(Constants.APP_PREFERENCES_PHONE_CALL)) {
            thatUser = intent.getSerializableExtra(Constants.APP_PREFERENCES_PHONE_CALL) as UserInformation
            onPhoneCallReceive(false, thatUser)
        }
        loadAvatar()
    }

    override fun onBackPressed() {
        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main_menu, menu)
        val searchView = menu.findItem(R.id.menu_search).actionView as android.support.v7.widget.SearchView
        searchView.setOnCloseListener {
            if (clientService!!.connected && !isLoading) {
                clientService!!.getDialogs()
                isLoading = true
                progressBar!!.visibility = ProgressBar.VISIBLE
            }
            return@setOnCloseListener false
        }
        searchView.setOnQueryTextListener(object: android.support.v7.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                if (clientService!!.connected && !isLoading) {
                    if (newText != null) {
                        clientService!!.findUser(newText, 10)
                    } else {
                        clientService!!.getDialogs()
                    }
                    isLoading = true
                    progressBar!!.visibility = ProgressBar.VISIBLE
                }
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                onQueryTextChange(query)
                return true
            }
        })
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBind) {
            //clientService!!.stopClient()
            clientService!!.removeActivity(this)
            unbindService(mConnection)
        }
        //stopService(serviceIntent)
        /* Clear memory */
        (navImageView!!.drawable as BitmapDrawable).bitmap.recycle()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            Constants.APP_PREFERENCES_LOGIN_ACTIVITY_REQUEST -> {
                when (resultCode) {
                    RESULT_OK -> loadUser()
                    RESULT_CANCELED -> finish()
                }
            }
            Constants.APP_PREFERENCES_CHATTING_ACTIVITY_REQUEST -> {
                adapter!!.users = ArrayList()
                updateAdapter()
                if (!mBind) {
                    mBind = bindService(serviceIntent, mConnection, BIND_AUTO_CREATE)
                }
            }
            Constants.APP_PREFERENCES_CHANGE_AVATAR_REQUEST -> {
                if (data != null) {
                    val path = data.getStringExtra(Constants.APP_PREFERENCES_FULLSCREEN_IMAGE)
                    SettingsUtil.saveAvatarPath(this, path)
                    navImageView!!.setImageBitmap(BitmapFactory.decodeFile(path))
                }
            }
        }
    }
    /* NavigationView override functions */

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dialogs -> {
                if (clientService!!.connected && !isLoading) {
                    clientService!!.getDialogs()
                    isLoading = true
                    progressBar!!.visibility = ProgressBar.VISIBLE
                }
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_exit -> {
                SettingsUtil.saveUserInformation(this, UserInformation())
                clientService!!.stopClient()
                loadUser()}
        }
        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    /* ClientService.CallBack override functions */

    override fun onJSONReceive(jsonObject: JSONObject) {
        val responseType = jsonObject[JSONKeys.JSON_KEY_RESPONSE_TYPE] as String
        when (responseType) {
            ServerKeys.ON_CHANGE_AVATAR -> {
                val path = jsonObject[JSONKeys.JSON_KEY_ATTACHMENT_NAME] as String
                SettingsUtil.saveAvatarPath(this, path)
                loadAvatar()
            }
            ServerKeys.CREATE_DIALOG -> {
                val login = jsonObject[JSONKeys.JSON_KEY_USER_LOGIN] as String
                Toast.makeText(this, getString(R.string.on_secret_chat_create).plus(login), Toast.LENGTH_SHORT).show()
                blockList.remove(login)
                updateAdapter()
            }
        }
    }

    override fun onConnectionStateChange(connected: Boolean) = if (connected) {
        navSubtitle!!.setTextColor(ContextCompat.getColor(this, R.color.colorGreen))
        navSubtitle!!.text = getString(R.string.online_label)
        progressBar!!.visibility = ProgressBar.GONE
        clientService!!.getDialogs()
        findViewById(R.id.textView)!!.visibility = View.VISIBLE
    } else {
        progressBar!!.visibility = ProgressBar.VISIBLE
        findViewById(R.id.textView)!!.visibility = View.INVISIBLE
        navSubtitle!!.setTextColor(ContextCompat.getColor(this, R.color.colorRed))
        navSubtitle!!.text = getString(R.string.connecting_label)
        adapter!!.users = ArrayList()
        updateAdapter()
    }

    override fun onPhoneCallReceive(isMy: Boolean, userInformation: UserInformation) = if (isMy) {
        onChangeCallState(CALL_STATE.OUTGOING_CONNECTED)
    } else {
        thatUser = userInformation
        onChangeCallState(CALL_STATE.INCOMING)
    }

    override fun onPhoneCallAnswer(result: Boolean) {
        onChangeCallState(CALL_STATE.DEACTIVATE)
        if (result) {
            startActivityForResult(Intent(this@UsersActivity, VideoChatActivity::class.java).
                    putExtra(Constants.APP_PREFERENCES_USER_REQUEST, thatUser),
                    Constants.APP_PREFERENCES_CHATTING_ACTIVITY_REQUEST)
        }
    }

    override fun onUsersListReceive(users: ArrayList<UserInformation>) {
        adapter!!.users = users
        updateAdapter()
        isLoading = false
        swipeRefreshLayout!!.isRefreshing = false
        progressBar!!.visibility = ProgressBar.GONE
    }

    override fun onChatMessageReceive(chatMessage: ChatMessage?, isDialog: Boolean) = Unit
}