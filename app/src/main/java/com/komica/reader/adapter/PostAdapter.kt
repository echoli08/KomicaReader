package com.komica.reader.adapter

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.komica.reader.R
import com.komica.reader.databinding.ItemPostBinding
import com.komica.reader.model.Post
import java.util.regex.Pattern

class PostAdapter(
    private val postFontSizeSp: Int,
    private val onQuoteClick: (Int) -> Unit,
    private val onQuotePreviewShow: (Post, View, Float, Float) -> Unit,
    private val onQuotePreviewHide: () -> Unit,
    private val onImageClick: (Int, List<String>) -> Unit,
    private val onImageLongClick: (String) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {
    private val items = mutableListOf<Post>()
    private val postNumberToPosition = mutableMapOf<Int, Int>()
    private val postPositionToImageIndex = mutableMapOf<Int, Int>()
    private val imageUrls = mutableListOf<String>()

    fun SubmitPosts(posts: List<Post>) {
        items.clear()
        items.addAll(posts)
        postNumberToPosition.clear()
        postPositionToImageIndex.clear()
        imageUrls.clear()
        posts.forEachIndexed { index, post ->
            if (post.number > 0) postNumberToPosition[post.number] = index
            if (post.imageUrl.isNotBlank()) {
                postPositionToImageIndex[index] = imageUrls.size
                imageUrls.add(post.imageUrl)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        return PostViewHolder(ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) = holder.Bind(items[position], position)

    inner class PostViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {
        private val handler = Handler(Looper.getMainLooper())
        private var pendingPreview: Runnable? = null
        private var pressedQuotePosition: Int? = null
        private var pressedQuoteX = 0f
        private var pressedQuoteY = 0f
        private var pressedUrl: String? = null
        private var previewShown = false

        fun Bind(post: Post, position: Int) {
            ClearQuoteTouch()
            val numberText = if (post.number > 0) "No.${post.number}" else "No.-"
            binding.postHeader.text = "$numberText　${post.author}　${post.time}"
            binding.postContent.textSize = postFontSizeSp.toFloat()
            binding.postContent.text = CreateQuoteText(post.content.ifBlank { "(無文字內容)" })
            SetupTextInteraction()

            val image = post.imageUrl.ifBlank { post.thumbnailUrl }
            if (image.isBlank()) {
                binding.postImage.visibility = View.GONE
                Glide.with(binding.postImage).clear(binding.postImage)
            } else {
                binding.postImage.visibility = View.VISIBLE
                Glide.with(binding.postImage).load(image).centerCrop().into(binding.postImage)
                binding.postImage.setOnClickListener {
                    val imageIndex = postPositionToImageIndex[position] ?: 0
                    onImageClick(imageIndex, imageUrls)
                }
                binding.postImage.setOnLongClickListener {
                    onImageLongClick(post.imageUrl.ifBlank { post.thumbnailUrl })
                    true
                }
            }
        }

        private fun CreateQuoteText(content: String): SpannableString {
            val spannable = SpannableString(content)
            val color = ContextCompat.getColor(binding.root.context, R.color.kr_secondary)
            ApplyUrlSpans(spannable, content)
            val quoteMatcher = QuotePattern.matcher(content)
            while (quoteMatcher.find()) {
                val postNumber = ExtractPostNumber(quoteMatcher) ?: continue
                if (!postNumberToPosition.containsKey(postNumber)) continue
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = Unit
                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = color
                        ds.isUnderlineText = true
                    }
                }, quoteMatcher.start(), quoteMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return spannable
        }

        private fun ApplyUrlSpans(spannable: SpannableString, content: String) {
            val matcher = UrlPattern.matcher(content)
            while (matcher.find()) {
                val rawUrl = matcher.group()
                val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = Unit
                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = ContextCompat.getColor(binding.root.context, R.color.kr_secondary)
                        ds.isUnderlineText = true
                    }
                }, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(UrlMarker(url), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun SetupTextInteraction() {
            binding.postContent.setTextIsSelectable(true)
            binding.postContent.setOnTouchListener { _, event ->
                val text = binding.postContent.text as? SpannableString ?: return@setOnTouchListener false
                val quotePosition = FindTouchedQuotePosition(text, event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        FindTouchedUrl(text, event)?.let { url ->
                            pressedUrl = url
                            return@setOnTouchListener true
                        }
                        if (quotePosition == null) return@setOnTouchListener false
                        pressedQuotePosition = quotePosition
                        pressedQuoteX = event.x
                        pressedQuoteY = event.y
                        previewShown = false
                        pendingPreview = Runnable {
                            previewShown = true
                            onQuotePreviewShow(items[quotePosition], binding.postContent, pressedQuoteX, pressedQuoteY)
                        }
                        handler.postDelayed(pendingPreview!!, LongPressDelayMs)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        pressedUrl?.let { url ->
                            OpenUrl(url)
                            ClearQuoteTouch()
                            return@setOnTouchListener true
                        }
                        val targetPosition = pressedQuotePosition
                        val shouldClick = targetPosition != null && !previewShown
                        ClearQuoteTouch()
                        if (shouldClick) {
                            onQuoteClick(targetPosition!!)
                        }
                        targetPosition != null
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        val hadQuote = pressedQuotePosition != null
                        ClearQuoteTouch()
                        hadQuote
                    }
                    else -> false
                }
            }
        }

        private fun FindTouchedQuotePosition(text: SpannableString, event: MotionEvent): Int? {
            if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_UP) return null
            val layout = binding.postContent.layout ?: return null
            val x = (event.x - binding.postContent.totalPaddingLeft + binding.postContent.scrollX).toInt()
            val y = (event.y - binding.postContent.totalPaddingTop + binding.postContent.scrollY).toInt()
            if (y < 0 || y > layout.height) return null
            val line = layout.getLineForVertical(y)
            val offset = layout.getOffsetForHorizontal(line, x.toFloat())
            val spans = text.getSpans(offset, offset, ClickableSpan::class.java)
            val span = spans.firstOrNull() ?: return null
            if (text.getSpans(text.getSpanStart(span), text.getSpanEnd(span), UrlMarker::class.java).isNotEmpty()) return null
            val quote = text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            val matcher = QuotePattern.matcher(quote)
            val postNumber = matcher.takeIf { it.find() }?.let { ExtractPostNumber(it) } ?: return null
            return postNumberToPosition[postNumber]
        }

        private fun FindTouchedUrl(text: SpannableString, event: MotionEvent): String? {
            if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_UP) return null
            val span = FindTouchedSpan(text, event) ?: return null
            return text.getSpans(text.getSpanStart(span), text.getSpanEnd(span), UrlMarker::class.java).firstOrNull()?.url
        }

        private fun FindTouchedSpan(text: SpannableString, event: MotionEvent): ClickableSpan? {
            val layout = binding.postContent.layout ?: return null
            val x = (event.x - binding.postContent.totalPaddingLeft + binding.postContent.scrollX).toInt()
            val y = (event.y - binding.postContent.totalPaddingTop + binding.postContent.scrollY).toInt()
            if (y < 0 || y > layout.height) return null
            val line = layout.getLineForVertical(y)
            val offset = layout.getOffsetForHorizontal(line, x.toFloat())
            return text.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
        }

        private fun OpenUrl(url: String) {
            binding.root.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        private fun ClearQuoteTouch() {
            pendingPreview?.let { handler.removeCallbacks(it) }
            pendingPreview = null
            pressedQuotePosition = null
            pressedUrl = null
            if (previewShown) {
                onQuotePreviewHide()
            }
            previewShown = false
        }
    }

    companion object {
        private val QuotePattern: Pattern = Pattern.compile(">>\\s*(?:No\\.?\\s*)?(\\d+)|>\\s*(?:No\\.?\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE)
        private val UrlPattern: Pattern = Pattern.compile("""(?i)\b((?:https?://)?(?:[a-z0-9-]+\.)+[a-z]{2,}(?::\d+)?(?:/[^\s<>"']*)?)""")
        private const val LongPressDelayMs = 350L

        private data class UrlMarker(val url: String)

        private fun ExtractPostNumber(matcher: java.util.regex.Matcher): Int? {
            return matcher.group(1)?.toIntOrNull() ?: matcher.group(2)?.toIntOrNull()
        }
    }
}
