package com.komica.reader.model

data class ReplySubmitResult(
    val success: Boolean,
    val needsVerification: Boolean,
    val message: String
)
