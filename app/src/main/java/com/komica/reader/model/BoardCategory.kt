package com.komica.reader.model

import java.io.Serializable

data class BoardCategory(
    val name: String,
    val boards: List<Board>,
    val isExpanded: Boolean = true
) : Serializable
