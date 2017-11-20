package helfi2012.chat.models

import java.io.Serializable

class UserInformation(var login: String, var password: String, var ipAddress: String, var iconPath: String, var online: Boolean,
                           var lastMessage: ChatMessage?) : Serializable {
    constructor() : this("","","","",false, null)
}