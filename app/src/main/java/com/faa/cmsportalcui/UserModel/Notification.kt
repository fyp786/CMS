package com.faa.cmsportalcui.UserModel



data class Notification(
    var id: String? = null,
    var title: String = "",
    var description: String = "",
    val date: Any? = null,
    var isRead: Boolean = false // Add this field to track read status
)
