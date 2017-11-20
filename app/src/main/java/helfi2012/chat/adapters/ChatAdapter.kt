package helfi2012.chat.adapters

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import helfi2012.chat.R
import helfi2012.chat.activities.ImageActivity
import helfi2012.chat.models.ChatMessage
import helfi2012.chat.utils.Constants
import helfi2012.chat.utils.Utils
import java.util.*

class ChatAdapter(private val context: Activity) : BaseAdapter() {

    companion object {
        private val IMAGE_RATIO = 0.6
    }
    val chatMessages: MutableList<ChatMessage> = ArrayList()
    var screenSize: Size? = null

    override fun getCount(): Int = chatMessages.size

    override fun getItem(position: Int): ChatMessage? = chatMessages[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int = position

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val holder: ViewHolder
        val chatMessage = getItem(position)!!

        if (view == null) {
            val vi = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = vi.inflate(R.layout.list_item_chat_message, null)!!
            holder = ViewHolder(view)
            view.tag = Pair(chatMessage, holder)
        } else {
            val pair = view.tag as Pair<*, *>
            //val oldMessage = pair.first as ChatMessage
            //if (oldMessage.time == chatMessage.time) return view
            holder = pair.second as ViewHolder
            holder.imageLayout!!.removeAllViews()
        }

        /* Create and load message attachments */
        for ((_, path, ratio) in chatMessage.attachments) {
            val imageView = ImageView(context)
            holder.imageLayout!!.addView(imageView)
            imageView.setOnClickListener { v: View ->
                context.startActivity(Intent(context, ImageActivity::class.java).
                        putExtra(Constants.APP_PREFERENCES_FULLSCREEN_IMAGE, v.tag as String))
            }
            imageView.tag = path
            // Set default drawable
            val res = if (chatMessage.isMy) {
                R.drawable.in_message_photo
            } else {
                R.drawable.out_message_photo
            }
            val size = minOf(screenSize!!.width, screenSize!!.height)
            val bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(context.resources, res),
                    (size * IMAGE_RATIO).toInt(), (size * ratio * IMAGE_RATIO).toInt(), false)
            imageView.setImageBitmap(bitmap)
            // Decode image from file
            AsyncLoadImages(imageView, path, ratio, size).execute()
        }

        setAlignment(holder, chatMessage.isMy)
        holder.txtMessage!!.text = chatMessage.text
        holder.txtInfo!!.text = Utils.formatTime(chatMessage.time)
        return view
    }

    /* Set start or end chat bubbles alignment */
    private fun setAlignment(holder: ViewHolder, isMe: Boolean) {
        val gravity: Int
        val drawable: Int
        val align1: Int
        val align2: Int
        if (isMe) {
            gravity = Gravity.END
            drawable = R.drawable.in_message_bg
            align1 = RelativeLayout.ALIGN_PARENT_LEFT
            align2 = RelativeLayout.ALIGN_PARENT_RIGHT
        } else {
            gravity = Gravity.START
            drawable = R.drawable.out_message_bg
            align1 = RelativeLayout.ALIGN_PARENT_RIGHT
            align2 = RelativeLayout.ALIGN_PARENT_LEFT
        }

        holder.contentWithBG!!.setBackgroundResource(drawable)

        var layoutParams = holder.contentWithBG!!.layoutParams as LinearLayout.LayoutParams
        layoutParams.gravity = gravity
        holder.contentWithBG!!.layoutParams = layoutParams

        val lp = holder.content!!.layoutParams as RelativeLayout.LayoutParams
        lp.addRule(align1, 0)
        lp.addRule(align2)
        holder.content!!.layoutParams = lp
        layoutParams = holder.txtMessage!!.layoutParams as LinearLayout.LayoutParams
        layoutParams.gravity = gravity
        holder.txtMessage!!.layoutParams = layoutParams

        layoutParams = holder.txtInfo!!.layoutParams as LinearLayout.LayoutParams
        layoutParams.gravity = gravity
        holder.txtInfo!!.layoutParams = layoutParams

        layoutParams = holder.imageLayout!!.layoutParams as LinearLayout.LayoutParams
        layoutParams.gravity = gravity
        holder.imageLayout!!.layoutParams = layoutParams

        for (i in 0 until holder.imageLayout!!.childCount) {
            val imageView = holder.imageLayout!!.getChildAt(i) as ImageView
            imageView.setPadding(10, 0, 10, 10)
            layoutParams = imageView.layoutParams as LinearLayout.LayoutParams
            layoutParams.gravity = gravity
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            imageView.layoutParams = layoutParams
        }
    }

    /* Container which hold list item views */
    private class ViewHolder(parentView: View) {
        var txtMessage: TextView? = null
        var txtInfo: TextView? = null
        var content: LinearLayout? = null
        var contentWithBG: LinearLayout? = null
        var imageLayout: LinearLayout? = null
        init {
            txtMessage = parentView.findViewById(R.id.txtMessage) as TextView
            content = parentView.findViewById(R.id.content) as LinearLayout
            contentWithBG = parentView.findViewById(R.id.contentWithBackground) as LinearLayout
            txtInfo = parentView.findViewById(R.id.loginTextView) as TextView
            imageLayout = parentView.findViewById(R.id.imageLayout) as LinearLayout
        }
    }

    /* AsyncTask which decodes bitmap from file */
    private  inner class AsyncLoadImages(val imageView: ImageView, val path: String, val ratio: Double, val size: Int) :
            AsyncTask<Void, Void, Boolean>() {
        private var bitmap: Bitmap? = null

        override fun doInBackground(vararg params: Void): Boolean {
            try {
                bitmap = BitmapFactory.decodeFile(path)
                bitmap = Bitmap.createScaledBitmap(bitmap, (size * IMAGE_RATIO).toInt(), (size * ratio * IMAGE_RATIO).toInt(), false)
            } catch (e: Exception) {}
            return bitmap != null
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                imageView.setImageBitmap(bitmap)
            }
            bitmap = null
        }
    }
}
