package com.komica.reader.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMessage {
    fun Build(action: String, throwable: Throwable): String {
        val detail = when (throwable) {
            is UnknownHostException -> "無法連線到網站，請確認網路或 DNS 設定。"
            is SocketTimeoutException -> "連線逾時，網站可能忙碌或網路不穩。"
            is IOException -> "網路連線失敗：${throwable.message.orEmpty().ifBlank { "請稍後再試" }}"
            else -> throwable.message.orEmpty().ifBlank { "未知錯誤" }
        }
        return "$action\n$detail"
    }
}
