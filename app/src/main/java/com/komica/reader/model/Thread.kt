package com.komica.reader.model

import java.io.Serializable

// 繁體中文註解：討論串資料模型，保留可變屬性以維持既有流程
data class Thread(
    var id: String,
    var title: String,
    var author: String,
    var replyCount: Int,
    var url: String,
    var postNumber: Int = 0,
    var imageUrl: String = "",
    var content: String = "",
    var contentPreview: String = "",
    var lastReplyTime: String = "",
    var posts: List<Post> = emptyList()
) : Serializable
