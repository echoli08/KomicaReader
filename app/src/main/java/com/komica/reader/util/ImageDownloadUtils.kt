package com.komica.reader.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.komica.reader.R
import java.io.File

object ImageDownloadUtils {
    const val PREFS_NAME = "KomicaReader"
    const val KEY_IMAGE_DOWNLOAD_PATH = "image_download_path"
    const val DOWNLOAD_PATH_PICTURES = "pictures"
    const val DOWNLOAD_PATH_DOWNLOADS = "downloads"

    fun getDownloadPathType(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_IMAGE_DOWNLOAD_PATH, DOWNLOAD_PATH_PICTURES)
            ?: DOWNLOAD_PATH_PICTURES
    }

    fun getDownloadPathLabel(context: Context, pathType: String): String {
        val resId = if (pathType == DOWNLOAD_PATH_DOWNLOADS) {
            R.string.setting_download_path_value_downloads
        } else {
            R.string.setting_download_path_value_pictures
        }
        return context.getString(resId)
    }

    fun getDownloadRelativePath(context: Context, subDir: String?): String {
        val pathType = getDownloadPathType(context)
        val rootDir = if (pathType == DOWNLOAD_PATH_DOWNLOADS) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            Environment.DIRECTORY_PICTURES
        }
        val basePath = "$rootDir/KomicaReader"
        return if (subDir.isNullOrBlank()) {
            basePath
        } else {
            "$basePath/$subDir"
        }
    }

    fun getMimeTypeFromExtension(extension: String): String {
        val ext = extension.lowercase().trim('.')
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: "image/jpeg"
    }

    fun saveImageToMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        relativePath: String,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 繁體中文註解：使用 RELATIVE_PATH 寫入公開相簿
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }

        return uri
    }
}
