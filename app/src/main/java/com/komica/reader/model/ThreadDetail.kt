package com.komica.reader.model

import java.io.Serializable

data class ThreadDetail(
    val title: String,
    val url: String,
    val posts: List<Post>
) : Serializable
