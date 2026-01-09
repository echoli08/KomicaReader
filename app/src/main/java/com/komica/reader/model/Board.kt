package com.komica.reader.model

import java.io.Serializable

// 繁體中文註解：看板資料模型，維持既有欄位結構
data class Board(
    var name: String,
    var url: String,
    var description: String
) : Serializable
