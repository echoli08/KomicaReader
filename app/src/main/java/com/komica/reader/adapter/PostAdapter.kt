package com.komica.reader.adapter

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.util.LruCache
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.komica.reader.R
import com.komica.reader.model.Post
import java.util.regex.Pattern

class PostAdapter(
    private val posts: List<Post>,
    private val interactionListener: OnQuoteInteractionListener?,
    private val contentTextSize: Float
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val allImageUrls: MutableList<String> = ArrayList()
    private val postToImageIndexMap: MutableMap<Int, Int> = HashMap()
    private val postIdToPositionMap: MutableMap<String, Int> = HashMap()
    // 繁體中文註解：快取已解析的引文內容，減少重複解析耗時
    private val spannableCache: LruCache<String, SpannableString> = LruCache(100)
    private var recyclerView: RecyclerView? = null

    interface OnQuoteInteractionListener {
        fun onImageClick(imageIndex: Int, imageUrls: List<String>)
        fun onImageLongClick(imageUrl: String)
        fun onQuoteClick(position: Int)
        fun onQuoteLongClick(post: Post)
        fun onQuoteReleased()
    }

    init {
        posts.forEachIndexed { index, post ->
            postIdToPositionMap[post.number.toString()] = index
            if (post.imageUrl.isNotBlank()) {
                allImageUrls.add(post.imageUrl)
                postToImageIndexMap[index] = allImageUrls.size - 1
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position], position)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun getItemCount(): Int = posts.size

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    private fun createSpannableContent(context: android.content.Context, content: String): SpannableString {
        val spannable = SpannableString(content)

        val quoteColor = ContextCompat.getColor(context, R.color.quote_text)
        val linkColor = ContextCompat.getColor(context, R.color.text_link)

        // 繁體中文註解：先處理綠字、引用與網址，避免重疊樣式
        val lines = content.split("\n")
        var currentPos = 0
        for (line in lines) {
            if (line.startsWith(">") && !line.matches("^>>?\\d+.*".toRegex())) {
                val end = (currentPos + line.length).coerceAtMost(spannable.length)
                spannable.setSpan(
                    ForegroundColorSpan(quoteColor),
                    currentPos,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            currentPos += line.length + 1
        }

        val matcher = QUOTE_PATTERN.matcher(content)
        while (matcher.find()) {
            val postId = matcher.group(1) ?: ""
            val targetPosition = postIdToPositionMap[postId]
            if (targetPosition != null) {
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {}

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = linkColor
                        ds.isUnderlineText = true
                    }
                }
                spannable.setSpan(
                    clickableSpan,
                    matcher.start(),
                    matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val urlMatcher = Patterns.WEB_URL.matcher(content)
        while (urlMatcher.find()) {
            val start = urlMatcher.start()
            val end = urlMatcher.end()
            val existingSpans = spannable.getSpans(start, end, ClickableSpan::class.java)
            if (existingSpans.isNotEmpty()) continue

            var url = urlMatcher.group()
            if (!url.startsWith("http")) {
                url = "https://$url"
            }
            val finalUrl = url

            val urlSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        widget.context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            widget.context,
                            widget.context.getString(R.string.error_open_link, finalUrl),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = linkColor
                    ds.isUnderlineText = true
                }
            }
            spannable.setSpan(urlSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannable
    }

    private fun getSpannableContent(context: android.content.Context, content: String): SpannableString {
        val cached = spannableCache.get(content)
        if (cached != null) {
            return cached
        }
        val created = createSpannableContent(context, content)
        spannableCache.put(content, created)
        return created
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val postNumber: TextView = itemView.findViewById(R.id.postNumber)
        private val postAuthor: TextView = itemView.findViewById(R.id.postAuthor)
        private val postContent: TextView = itemView.findViewById(R.id.postContent)
        private val postTime: TextView = itemView.findViewById(R.id.postTime)
        private val postImage: ImageView = itemView.findViewById(R.id.postImage)
        private val handler = Handler(Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null
        private var isLongPressed = false
        private var pressedSpan: ClickableSpan? = null

        init {
            itemView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    cleanup()
                }
            })
        }

        fun cleanup() {
            longPressRunnable?.let { handler.removeCallbacks(it) }
            longPressRunnable = null
        }

        fun bind(post: Post, position: Int) {
            postContent.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, contentTextSize)

            postNumber.text = "No. ${post.number}"
            val authorText = post.author.ifBlank { itemView.context.getString(R.string.text_unknown_author) }
            postAuthor.text = authorText
            postTime.text = post.time

            if (post.content.isBlank()) {
                postContent.text = itemView.context.getString(R.string.text_no_content)
                postContent.visibility = View.GONE
                postContent.setOnTouchListener(null)
            } else {
                val spannable = getSpannableContent(itemView.context, post.content)
                postContent.text = spannable
                postContent.movementMethod = null
                postContent.visibility = View.VISIBLE
                setupTouchListener(spannable)
            }

            val displayImageUrl = when {
                post.imageUrl.isNotBlank() -> post.imageUrl
                post.thumbnailUrl.isNotBlank() -> post.thumbnailUrl
                else -> ""
            }

            if (displayImageUrl.isNotBlank()) {
                postImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(displayImageUrl)
                    // 繁體中文註解：限制圖片解碼尺寸與格式，降低記憶體占用
                    .override(600, 600)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(postImage)

                val imageIndex = postToImageIndexMap[position]
                if (imageIndex != null) {
                    postImage.setOnClickListener {
                        interactionListener?.onImageClick(imageIndex, allImageUrls)
                    }
                    postImage.setOnLongClickListener {
                        interactionListener?.onImageLongClick(displayImageUrl)
                        true
                    }
                } else {
                    postImage.setOnClickListener(null)
                    postImage.setOnLongClickListener(null)
                }
            } else {
                postImage.visibility = View.GONE
                postImage.setOnClickListener(null)
                postImage.setOnLongClickListener(null)
            }
        }

        private fun setupTouchListener(spannable: SpannableString) {
            postContent.setOnTouchListener { v, event ->
                val action = event.action
                val widget = v as TextView

                if (action == MotionEvent.ACTION_DOWN) {
                    pressedSpan = null
                    var x = event.x.toInt()
                    var y = event.y.toInt()

                    x -= widget.totalPaddingLeft
                    y -= widget.totalPaddingTop
                    x += widget.scrollX
                    y += widget.scrollY

                    val layout: Layout = widget.layout ?: return@setOnTouchListener false
                    val line = layout.getLineForVertical(y)
                    val off = layout.getOffsetForHorizontal(line, x.toFloat())

                    val links = spannable.getSpans(off, off, ClickableSpan::class.java)
                    if (links.isNotEmpty()) {
                        val target = links.firstOrNull { link ->
                            val start = spannable.getSpanStart(link)
                            val end = spannable.getSpanEnd(link)
                            val text = spannable.subSequence(start, end).toString()
                            text.matches("^>>?\\d+$".toRegex())
                        } ?: links[0]

                        pressedSpan = target
                        val start = spannable.getSpanStart(target)
                        val end = spannable.getSpanEnd(target)
                        val linkText = spannable.subSequence(start, end).toString()

                        val matcher = QUOTE_PATTERN.matcher(linkText)
                        if (matcher.find()) {
                            val postId = matcher.group(1) ?: ""
                            val targetPos = postIdToPositionMap[postId]
                            if (targetPos != null) {
                                isLongPressed = false
                                longPressRunnable = Runnable {
                                    isLongPressed = true
                                    v.parent.requestDisallowInterceptTouchEvent(true)
                                    interactionListener?.onQuoteLongClick(posts[targetPos])
                                }
                                // 繁體中文註解：長按引文觸發預覽
                                handler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY_MS)
                                return@setOnTouchListener true
                            }
                        } else {
                            return@setOnTouchListener true
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    v.parent.requestDisallowInterceptTouchEvent(false)

                    if (isLongPressed) {
                        isLongPressed = false
                        interactionListener?.onQuoteReleased()
                        pressedSpan = null
                        return@setOnTouchListener true
                    } else if (action == MotionEvent.ACTION_UP && pressedSpan != null) {
                        val start = spannable.getSpanStart(pressedSpan)
                        val end = spannable.getSpanEnd(pressedSpan)
                        val linkText = spannable.subSequence(start, end).toString()
                        val upMatcher = QUOTE_PATTERN.matcher(linkText)

                        if (upMatcher.find()) {
                            val postId = upMatcher.group(1) ?: ""
                            val targetPos = postIdToPositionMap[postId]
                            if (targetPos != null) {
                                interactionListener?.onQuoteClick(targetPos)
                                recyclerView?.findViewHolderForAdapterPosition(targetPos)?.let { holder ->
                                    val highlightColor = ContextCompat.getColor(
                                        holder.itemView.context,
                                        R.color.highlight_background
                                    )
                                    holder.itemView.setBackgroundColor(highlightColor)
                                    holder.itemView.postDelayed({
                                        holder.itemView.setBackgroundColor(0x00000000)
                                    }, HIGHLIGHT_DURATION_MS)
                                }
                            }
                        } else {
                            pressedSpan?.onClick(widget)
                        }
                        pressedSpan = null
                        return@setOnTouchListener true
                    }
                    pressedSpan = null
                }
                false
            }
        }
    }

    companion object {
        private val QUOTE_PATTERN: Pattern = Pattern.compile(">>?(\\d+)")
        private const val LONG_PRESS_DELAY_MS = 500L
        private const val HIGHLIGHT_DURATION_MS = 1000L
    }
}
