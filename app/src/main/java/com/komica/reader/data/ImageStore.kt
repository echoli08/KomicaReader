package com.komica.reader.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request

object ImageStore {
    private val Client = OkHttpClient()

    fun DownloadImage(context: Context, imageUrl: String): Boolean {
        val response = Client.newCall(Request.Builder().url(imageUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) return false
            val body = it.body ?: return false
            val mimeType = body.contentType()?.toString() ?: GuessMimeType(imageUrl)
            val bytes = body.bytes()
            return SaveImageBytes(context, imageUrl, bytes, mimeType)
        }
    }

    private fun SaveImageBytes(context: Context, imageUrl: String, bytes: ByteArray, mimeType: String): Boolean {
        val settings = AppSettings(context)
        val fileName = "komica_${System.currentTimeMillis()}.${GuessExtension(imageUrl, mimeType)}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${MediaDirectory(mimeType)}/${settings.downloadFolderName}")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaCollection(mimeType), values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        return true
    }

    private fun GuessMimeType(imageUrl: String): String {
        return when (imageUrl.substringBefore('?').substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "webm" -> "video/webm"
            else -> "image/jpeg"
        }
    }

    private fun GuessExtension(imageUrl: String, mimeType: String): String {
        val urlExtension = imageUrl.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (urlExtension in setOf("jpg", "jpeg", "png", "gif", "webp", "webm")) return urlExtension
        return when (mimeType.substringBefore(';').lowercase()) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "video/webm" -> "webm"
            else -> "jpg"
        }
    }

    private fun MediaCollection(mimeType: String): Uri {
        return if (mimeType.substringBefore(';').equals("video/webm", ignoreCase = true)) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun MediaDirectory(mimeType: String): String {
        return if (mimeType.substringBefore(';').equals("video/webm", ignoreCase = true)) {
            Environment.DIRECTORY_MOVIES
        } else {
            Environment.DIRECTORY_PICTURES
        }
    }
}
