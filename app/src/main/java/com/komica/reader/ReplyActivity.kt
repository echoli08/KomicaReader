package com.komica.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.komica.reader.data.AppSettings
import com.komica.reader.data.KomicaRepository
import com.komica.reader.databinding.ActivityReplyBinding
import com.komica.reader.model.ReplyForm
import com.komica.reader.model.ReplySubmitRequest
import com.komica.reader.util.WindowInsetsUtil
import kotlinx.coroutines.launch

class ReplyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReplyBinding
    private val repository = KomicaRepository()
    private var threadUrl = ""
    private var replyForm: ReplyForm? = null
    private var selectedImageUri: Uri? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        binding.imageText.text = uri?.lastPathSegment?.let { "已選擇：$it" }.orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityReplyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.ApplyToolbarInsets(binding.toolbar)

        threadUrl = intent.getStringExtra(ExtraUrl).orEmpty()
        if (threadUrl.isBlank()) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = intent.getStringExtra(ExtraTitle).orEmpty().ifBlank { "回覆" }
        binding.imageButton.setOnClickListener { imagePicker.launch("image/*") }
        binding.submitButton.setOnClickListener { SubmitReply() }
        binding.browserButton.setOnClickListener { OpenExternalBrowser() }
        LoadReplyForm()
    }

    private fun LoadReplyForm() {
        SetLoading(true, "正在讀取網站回覆表單...")
        lifecycleScope.launch {
            val result = runCatching { repository.LoadReplyForm(threadUrl) }
            replyForm = result.getOrNull()
            SetLoading(false, result.exceptionOrNull()?.message ?: "已讀取網站表單，將依照原站欄位送出。")
            binding.submitButton.isEnabled = replyForm != null
        }
    }

    private fun SubmitReply() {
        val form = replyForm ?: return
        val content = binding.contentEdit.text.toString()
        if (content.isBlank() && selectedImageUri == null) {
            Toast.makeText(this, "請輸入回覆內容或選擇圖片", Toast.LENGTH_SHORT).show()
            return
        }

        SetLoading(true, "正在送出回覆...")
        lifecycleScope.launch {
            val request = ReplySubmitRequest(
                form = form,
                threadUrl = threadUrl,
                name = binding.nameEdit.text.toString(),
                email = binding.emailEdit.text.toString(),
                title = binding.titleEdit.text.toString(),
                content = content,
                password = binding.passwordEdit.text.toString(),
                imageUri = selectedImageUri
            )
            val result = runCatching { repository.SubmitReply(this@ReplyActivity, request) }
            val submitResult = result.getOrNull()
            SetLoading(false, submitResult?.message ?: result.exceptionOrNull()?.message.orEmpty().ifBlank { "回覆送出失敗" })
            when {
                submitResult?.success == true -> CompleteReplyFlow()
                submitResult?.needsVerification == true -> OpenExternalBrowser()
                else -> Toast.makeText(this@ReplyActivity, binding.statusText.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun SetLoading(isLoading: Boolean, message: String) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.submitButton.isEnabled = !isLoading && replyForm != null
        binding.imageButton.isEnabled = !isLoading
        binding.browserButton.isEnabled = !isLoading
        binding.statusText.text = message
    }

    private fun OpenExternalBrowser() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(threadUrl)))
    }

    private fun CompleteReplyFlow() {
        Toast.makeText(this, "回覆已送出", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        const val ExtraUrl = "extra_url"
        const val ExtraTitle = "extra_title"
    }
}
