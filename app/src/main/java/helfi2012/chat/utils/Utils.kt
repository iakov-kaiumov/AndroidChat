package helfi2012.chat.utils

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.support.v7.app.AlertDialog
import helfi2012.chat.activities.UsersActivity
import java.text.SimpleDateFormat
import java.util.*


object Utils {

    fun formatTime(timeMillis: Long): String {
        val date1 = GregorianCalendar()
        date1.timeInMillis = System.currentTimeMillis()
        val date2 = GregorianCalendar()
        date2.timeInMillis = timeMillis
        var dateFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)
        if (date1.get(GregorianCalendar.MONTH) == date2.get(GregorianCalendar.MONTH) &&
                date1.get(GregorianCalendar.DAY_OF_MONTH) == date2.get(GregorianCalendar.DAY_OF_MONTH)) {
            dateFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        }       //val dateFormat = SimpleDateFormat("HH:mm dd MMM", Locale.ENGLISH)
        return dateFormat.format(Date(timeMillis))
    }

    fun createAlertDialog(context: Context, title: String, message: String, positiveButtonText: String,
                                      positiveButtonAction: () -> Unit, negativeButtonText: String, negativeButtonAction: () -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(positiveButtonText) { dialog, _ ->
            positiveButtonAction.invoke()
            dialog.cancel()
        }
        builder.setNegativeButton(negativeButtonText) { dialog, _ ->
            negativeButtonAction.invoke()
            dialog.cancel()
        }
        builder.create().show()
    }

    fun createNotification(context: Context, id: Int, title: String, text: String, autoCancel: Boolean, smallIcon: Int, largeIcon: Bitmap?,
                           vibrate: LongArray?, sound: Uri?, intent: PendingIntent?) {
        val applicationContext = context.applicationContext
        val notificationIntent = Intent(context, UsersActivity::class.java)
        val contentIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val builder = Notification.Builder(applicationContext)

        builder.setContentIntent(contentIntent)
                .setSmallIcon(smallIcon)
                .setLargeIcon(largeIcon)
                .setAutoCancel(autoCancel)
                .setContentTitle(title)
                .setContentText(text)
        val notification = builder.build()
        if (vibrate == null) {
            notification.defaults = notification.defaults or Notification.DEFAULT_VIBRATE
        } else {
            notification.vibrate = vibrate
        }
        if (sound == null) {
            //notification.defaults = notification.defaults or Notification.DEFAULT_SOUND
        } else {
            notification.sound = sound
        }
        //notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notification.contentIntent = intent

        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification)
    }

    fun destroyNotification(context: Context, id: Int) =
            (context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)

    fun isMyServiceRunning(context: Context, serviceClass: Class<*>): Boolean =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                    .getRunningServices(Integer.MAX_VALUE).any { serviceClass.name == it.service.className }
}
