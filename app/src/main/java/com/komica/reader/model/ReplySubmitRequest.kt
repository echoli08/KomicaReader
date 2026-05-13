package com.komica.reader.model

import android.net.Uri

data class ReplySubmitRequest(
    val form: ReplyForm,
    val threadUrl: String,
    val name: String,
    val email: String,
    val title: String,
    val content: String,
    val password: String,
    val imageUri: Uri?
)
