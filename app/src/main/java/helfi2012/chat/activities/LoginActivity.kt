package helfi2012.chat.activities

import android.support.v7.app.AppCompatActivity
import android.os.AsyncTask

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.support.v4.app.ActivityCompat
import helfi2012.chat.*
import helfi2012.chat.models.UserInformation
import helfi2012.chat.tcpconnection.JSONKeys
import helfi2012.chat.tcpconnection.ServerKeys
import helfi2012.chat.tcpconnection.TCPClient
import helfi2012.chat.utils.Constants
import helfi2012.chat.utils.SettingsUtil

class LoginActivity : AppCompatActivity() {

    private var mAuthTask: UserLoginTask? = null

    private var mLoginView: AutoCompleteTextView? = null
    private var mPasswordView: EditText? = null
    private var mSignInButton: Button? = null
    private var mProgressBar: ProgressBar? = null

    private var mClient: TCPClient? = null

    private fun askPermissions() {
        for (permission in Constants.PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, Constants.PERMISSIONS, Constants.APP_PREFERENCES_PERMISSION_REQUEST)
                break
            }
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED, Intent())
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        askPermissions()

        mLoginView = findViewById(R.id.email) as AutoCompleteTextView

        mPasswordView = findViewById(R.id.password) as EditText
        mPasswordView!!.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })

        mSignInButton = findViewById(R.id.sign_in_button) as Button
        mSignInButton!!.setOnClickListener { attemptLogin() }

        val checkBox = findViewById(R.id.sign_in_type_check_box) as CheckBox
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mSignInButton!!.text = getString(R.string.action_register)
            } else {
                mSignInButton!!.text = getString(R.string.action_sign_in)
            }
        }

        mProgressBar = findViewById(R.id.login_progress) as ProgressBar
    }

    private fun attemptLogin() {
        if (mAuthTask != null) {
            return
        }

        mLoginView!!.error = null
        mPasswordView!!.error = null

        val email = mLoginView!!.text.toString()
        val password = mPasswordView!!.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView!!.error = getString(R.string.error_field_required)
            focusView = mPasswordView
            cancel = true
        } else if (!isPasswordValid(password)) {
            mPasswordView!!.error = getString(R.string.error_invalid_password)
            focusView = mPasswordView
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mLoginView!!.error = getString(R.string.error_field_required)
            focusView = mLoginView
            cancel = true
        } else if (!isEmailValid(email)) {
            mLoginView!!.error = getString(R.string.error_invalid_email)
            focusView = mLoginView
            cancel = true
        }

        if (cancel) {
            focusView!!.requestFocus()
        } else {
            mProgressBar!!.visibility = ProgressBar.VISIBLE
            mAuthTask = UserLoginTask(email, password, mSignInButton!!.text == getString(R.string.action_register))
            mAuthTask!!.execute(null as Void?)
        }
    }

    private fun isEmailValid(login: String): Boolean = login.length >= Constants.LOGIN_MIN_LENGTH

    private fun isPasswordValid(password: String): Boolean =
            password.length >= Constants.PASSWORD_MIN_LENGTH

    inner class UserLoginTask internal constructor(private val mLogin: String, private val mPassword: String, private val isNew: Boolean) :
            AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void): Boolean? {
            mClient = TCPClient(Constants.TCP_PORT, Constants.SERVER_HOST)
            mClient!!.externalStorageDirectory = Environment.getExternalStorageDirectory().toString()
            while (true) {
                if (!mClient!!.isCreated) return null
                val jsonObject = mClient!!.response
                if (jsonObject != null) {
                    val responseType = jsonObject[JSONKeys.JSON_KEY_RESPONSE_TYPE] as String
                    when (responseType) {
                        ServerKeys.ON_SUCCESSFUL_CONNECT -> {
                            if (isNew) {
                                mClient!!.register(mLogin, mPassword)
                            } else {
                                mClient!!.login(mLogin, mPassword)
                            }
                        }
                        ServerKeys.ON_SUCCESSFUL_REG -> return true
                        ServerKeys.ON_UNSUCCESSFUL_REG -> return false
                        ServerKeys.ON_SUCCESSFUL_LOGIN -> return true
                        ServerKeys.ON_UNSUCCESSFUL_LOGIN -> return false
                    }
                }
            }
        }

        override fun onPostExecute(success: Boolean?) {
            mAuthTask = null
            mProgressBar!!.visibility = ProgressBar.GONE
            mClient!!.close()

            if (success == null) {
                Toast.makeText(this@LoginActivity, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            } else
            if (success) {
                val user = UserInformation()
                user.login = mLogin
                user.password = mPassword
                SettingsUtil.saveUserInformation(this@LoginActivity, user)

                setResult(RESULT_OK, Intent())
                finish()
            } else {
                if (isNew) {
                    mLoginView!!.error = getString(R.string.error_user_exist)
                    mLoginView!!.requestFocus()
                } else {
                    mLoginView!!.error = getString(R.string.error_incorrect_data)
                    mLoginView!!.requestFocus()
                }
            }
        }

        override fun onCancelled() {
            mAuthTask = null
            mProgressBar!!.visibility = ProgressBar.GONE
        }
    }
}
