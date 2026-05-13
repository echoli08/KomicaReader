package com.komica.reader.model

data class ReplyForm(
    val actionUrl: String,
    val method: String,
    val hiddenFields: Map<String, String>,
    val nameField: String,
    val emailField: String,
    val titleField: String,
    val contentField: String,
    val passwordField: String,
    val fileField: String
)
