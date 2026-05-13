package com.komica.reader.model

import java.io.Serializable

data class Post(
    val id: String,
    val author: String,
    val content: String,
    val imageUrl: String = "",
    val thumbnailUrl: String = "",
    val time: String = "",
    val number: Int = 0
) : Serializable
