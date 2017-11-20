package helfi2012.chat.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import com.ortiz.touch.TouchImageView
import helfi2012.chat.utils.Constants
import helfi2012.chat.R
import helfi2012.chat.tcpconnection.TCPClient
import helfi2012.chat.utils.Utils
import java.io.File
import java.io.FileOutputStream

class ImageActivity : AppCompatActivity() {

    private var progressBar: ProgressBar? = null
    private var imageView: TouchImageView? = null
    private var textView: TextView? = null
    private var thisUser: String? = null
    private var avatarPath: String? = null

    private fun captureImage() {
        avatarPath = Environment.getExternalStorageDirectory().toString() + Constants.APP_PREFERENCES_DIRECTORY_BASE +
                System.currentTimeMillis() + ".jpeg"
        startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).
                putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(File(avatarPath))),
                Constants.APP_PREFERENCES_REQUEST_CODE_CAMERA_PHOTO)
    }

    private fun addPhoto(bitmap: Bitmap) {
        progressBar!!.startAnimation(AnimationUtils.loadAnimation(this, R.anim.progress_bar_rotate))
        imageView!!.setImageBitmap(bitmap)
        avatarPath = Environment.getExternalStorageDirectory().toString() + Constants.APP_PREFERENCES_DIRECTORY_BASE + thisUser +
                Constants.AVATAR_PATH_PREFIX + System.currentTimeMillis() + Constants.IMAGE_TYPE
        Thread({
            val fileOutputStream = FileOutputStream(avatarPath)
            bitmap.compress(Bitmap.CompressFormat.JPEG, Constants.DEFAULT_PICTURE_QUALITY, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            val client = TCPClient(Constants.TCP_PORT, Constants.SERVER_HOST)
            client.sendAttachment(avatarPath!!, {runOnUiThread {
                Utils.createAlertDialog(this@ImageActivity, getString(R.string.error_title),
                        getString(R.string.error_network), getString(R.string.ok_title), {}, getString(R.string.cancel_title), {})
            }},
                    {progress: Int ->  progressBar!!.progress = progress},
                    {
                    client.changeAvatar(thisUser!!, avatarPath!!)
                    client.close()
                    runOnUiThread {
                        setResult(RESULT_OK, Intent().putExtra(Constants.APP_PREFERENCES_FULLSCREEN_IMAGE, avatarPath))
                        finish()
                    }
                    })
        }).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image)
        supportActionBar!!.hide()
        progressBar = findViewById(R.id.avatar_progress) as ProgressBar
        imageView = findViewById(R.id.image_view) as TouchImageView
        textView = findViewById(R.id.text_view) as TextView

        when {
            intent.type == Constants.APP_PREFERENCES_INTENT_GALLERY_TYPE -> {
                thisUser = intent.getStringExtra(Constants.APP_PREFERENCES_USER_REQUEST)
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = Constants.APP_PREFERENCES_INTENT_GALLERY_TYPE
                startActivityForResult(intent, Constants.APP_PREFERENCES_REQUEST_CODE_GALLERY_PHOTO)
            }
            intent.type == Constants.APP_PREFERENCES_INTENT_CAPTURE_TYPE -> {
                thisUser = intent.getStringExtra(Constants.APP_PREFERENCES_USER_REQUEST)
                captureImage()
            }
            else -> {
                textView!!.visibility = TextView.GONE
                imageView!!.setImageURI(Uri.fromFile(File(intent.getStringExtra(Constants.APP_PREFERENCES_FULLSCREEN_IMAGE))))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Constants.APP_PREFERENCES_REQUEST_CODE_CAMERA_PHOTO) {
            if (resultCode == RESULT_OK) {
                val file = File(avatarPath)
                if (file.exists()) {
                    addPhoto(BitmapFactory.decodeFile(file.absolutePath))
                }
            }
        } else if (requestCode == Constants.APP_PREFERENCES_REQUEST_CODE_GALLERY_PHOTO) {
            if (resultCode == RESULT_OK) {
                avatarPath = data!!.data.path
                addPhoto(BitmapFactory.decodeStream(contentResolver.openInputStream(data.data)))
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
