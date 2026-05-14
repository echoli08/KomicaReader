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
    private var hasRequestedVerification = false

    private val verificationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            LoadReplyForm()
        } else if (replyForm == null) {
            SetLoading(false, "尚未完成網站驗證，無法讀取回覆表單。")
        }
    }

    private val webReplyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            CompleteReplyFlow()
        }
    }

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
        binding.browserButton.setOnClickListener { OpenWebReplyPage() }
        StartVerification()
    }

    private fun LoadReplyForm() {
        SetLoading(true, "正在讀取網站回覆表單...")
        lifecycleScope.launch {
            val result = runCatching { repository.LoadReplyForm(threadUrl) }
            replyForm = result.getOrNull()
            val errorMessage = result.exceptionOrNull()?.message
            if (replyForm == null && ShouldOpenVerification(errorMessage)) {
                SetLoading(false, "原生回覆無法通過站台驗證，已切換至網站回覆頁。")
                OpenWebReplyPage()
                return@launch
            }
            SetLoading(false, errorMessage ?: "已讀取網站表單，將依照原站欄位送出。")
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
                submitResult?.needsVerification == true -> OpenWebReplyPage()
                else -> Toast.makeText(this@ReplyActivity, binding.statusText.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun StartVerification() {
        hasRequestedVerification = true
        SetLoading(false, "請先完成網站驗證，完成後按下「我已完成驗證」。")
        verificationLauncher.launch(Intent(this, VerificationActivity::class.java).putExtra(VerificationActivity.ExtraUrl, threadUrl))
    }

    private fun ShouldOpenVerification(message: String?): Boolean {
        val text = message.orEmpty()
        return text.contains("驗證") ||
            text.contains("Cloudflare", ignoreCase = true) ||
            text.contains("503") ||
            text.contains("403") ||
            text.contains("429")
    }

    private fun SetLoading(isLoading: Boolean, message: String) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.submitButton.isEnabled = !isLoading && replyForm != null
        binding.imageButton.isEnabled = !isLoading
        binding.browserButton.isEnabled = !isLoading
        binding.statusText.text = message
    }

    private fun OpenWebReplyPage() {
        webReplyLauncher.launch(Intent(this, WebReplyActivity::class.java).apply {
            putExtra(WebReplyActivity.ExtraUrl, threadUrl)
            putExtra(WebReplyActivity.ExtraTitle, binding.toolbar.title?.toString().orEmpty())
        })
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
