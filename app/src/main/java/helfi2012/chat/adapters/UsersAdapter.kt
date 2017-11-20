package helfi2012.chat.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import de.hdodenhof.circleimageview.CircleImageView
import helfi2012.chat.utils.Constants
import helfi2012.chat.R
import helfi2012.chat.models.UserInformation
import helfi2012.chat.utils.Utils
import helfi2012.chat.activities.ImageActivity
import java.util.*

class UsersAdapter(private val context: Context) : BaseAdapter() {
    var users: List<UserInformation> = ArrayList()

    override fun getCount(): Int = users.size

    override fun getItem(position: Int): UserInformation = users[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int = position

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val holder: ViewHolder
        val userInformation = getItem(position)
        if (view == null || view.tag == null) {
            val vi = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = vi.inflate(R.layout.list_item_users_list, null)
            holder = ViewHolder(view)
            view!!.tag = holder
        } else {
            holder = view.tag as ViewHolder
        }

        holder.loginTextView!!.text = userInformation.login

        /* Displaying last message if it's exist */
        if (userInformation.lastMessage != null) {
            if (userInformation.lastMessage!!.text.isEmpty()) {
                holder.messageTextView!!.text = context.getString(R.string.attachment_label)
            } else {
                holder.messageTextView!!.text = userInformation.lastMessage!!.text
            }
            holder.timeTextView!!.text = Utils.formatTime(userInformation.lastMessage!!.time)
        } else {
            holder.messageTextView!!.text = ""
            holder.timeTextView!!.text = ""
        }

        /* Displaying online mark and call button if user is online */
        if (userInformation.online) {
            holder.onlineMark!!.visibility = View.VISIBLE
        } else {
            holder.onlineMark!!.visibility = View.INVISIBLE
        }
        /* Open fullscreen activity by click */
        holder.userIcon!!.setOnClickListener {
            if (userInformation.iconPath.isNotEmpty()) {
                context.startActivity(Intent(context, ImageActivity::class.java).
                        putExtra(Constants.APP_PREFERENCES_FULLSCREEN_IMAGE, userInformation.iconPath))
            }
        }
        /* check if is it necessary to load image */
        if (holder.userIcon!!.tag != userInformation.iconPath) {
            holder.userIcon!!.tag = userInformation.iconPath
            // Load default drawable
            holder.userIcon!!.setImageResource(R.drawable.person_icon)
            // Decode image from file, if file exists
            if (userInformation.iconPath.isNotEmpty()) {
                AsyncLoadImages(holder.userIcon!!, userInformation.iconPath).execute()
            }
        }
        return view
    }

    /* Container which hold list item views */
    private inner class ViewHolder(parentView: View) {
        var content: LinearLayout? = null
        var loginTextView: TextView? = null
        var messageTextView: TextView? = null
        var timeTextView: TextView? = null
        var onlineMark: View? = null
        var userIcon: CircleImageView? = null
        init {
            content = parentView.findViewById(R.id.content) as LinearLayout
            loginTextView = parentView.findViewById(R.id.loginTextView) as TextView
            messageTextView = parentView.findViewById(R.id.messageTextView) as TextView
            timeTextView = parentView.findViewById(R.id.timeTextView) as TextView
            onlineMark = parentView.findViewById(R.id.onlineMark)
            onlineMark!!.bringToFront()
            userIcon = parentView.findViewById(R.id.userIcon) as CircleImageView
            userIcon!!.setImageResource(R.drawable.person_icon)
            userIcon!!.tag = ""
        }
    }

    /* AsyncTask which decodes bitmap from file */
    private inner class AsyncLoadImages(private val userIcon: CircleImageView, private val path: String) :
            AsyncTask<Void, Void, Boolean>() {
        private var bitmap: Bitmap? = null

        override fun doInBackground(vararg params: Void): Boolean {
            try {
                val width = userIcon.layoutParams.width
                bitmap = BitmapFactory.decodeFile(path)
                bitmap = Bitmap.createScaledBitmap(bitmap, width,
                        (width * bitmap!!.height.toDouble() / bitmap!!.width.toDouble()).toInt(),
                        false)
            } catch (e: Exception) {
                return false
            }
            return bitmap != null
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                userIcon.setImageBitmap(bitmap)
            }
            bitmap = null
        }
    }
}
