package com.komica.reader.model

import java.io.Serializable

// 繁體中文註解：回覆貼文資料模型，保持欄位可修改
data class Post(
    var id: String,
    var author: String,
    var content: String,
    var imageUrl: String,
    var thumbnailUrl: String,
    var time: String,
    var number: Int
) : Serializable
