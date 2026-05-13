package com.komica.reader.model

import java.io.Serializable

// 繁體中文註解：看板基本資料，使用 Serializable 方便 Activity 傳遞。
data class Board(
    val name: String,
    val url: String,
    val description: String = "",
    val categoryName: String = ""
) : Serializable
