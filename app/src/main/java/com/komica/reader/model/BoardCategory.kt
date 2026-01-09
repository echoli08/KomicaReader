package com.komica.reader.model

import java.io.Serializable

// 繁體中文註解：看板分類模型，預設未展開
data class BoardCategory(
    var name: String,
    var boards: List<Board>,
    var isExpanded: Boolean = false
) : Serializable
