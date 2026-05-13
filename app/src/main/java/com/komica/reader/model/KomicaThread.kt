package com.komica.reader.model

import java.io.Serializable

data class KomicaThread(
    val id: String,
    val title: String,
    val author: String,
    val replyCount: Int,
    val url: String,
    val postNumber: Int = 0,
    val imageUrl: String = "",
    val contentPreview: String = "",
    val lastReplyTime: String = "",
    val lastReplySortKey: Long = 0L
) : Serializable
