package helfi2012.chat.models

import helfi2012.chat.models.Attachment
import java.io.Serializable
import java.util.ArrayList

data class ChatMessage(val name: String, var text: String, val time: Long): Serializable {
    var attachments: ArrayList<Attachment> = ArrayList()
    var isMy = false
}