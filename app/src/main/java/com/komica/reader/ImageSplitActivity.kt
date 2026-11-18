package com.komica.reader

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.komica.reader.adapter.ImageSplitPagerAdapter
import com.komica.reader.data.AppSettings
import com.komica.reader.databinding.ActivityImageSplitBinding
import com.komica.reader.databinding.DialogSplitPagesBinding
import com.komica.reader.util.ImageContentAnalyzer
import com.komica.reader.util.WindowInsetsUtil
import com.komica.reader.widget.ZoomImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.ceil
import java.io.File
import java.util.concurrent.Future

class ImageSplitActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageSplitBinding
    private lateinit var settings: AppSettings
    private var imageUrls: List<String> = emptyList()
    private var imageIndex: Int = 0
    private var currentMode: String = ModeLeftRight
    private var imageFile: File? = null
    private var imageWidth = 0
    private var imageHeight = 0
    private var contentGutters: List<ImageContentAnalyzer.Gutter> = emptyList()
    private var manualPageCount: Int? = null
    // 使用者指定頁數後記住的單頁比例（頁高 / 圖寬），切換下一張圖時據此重算頁數。
    private var manualPageRatio: Float? = null
    private var pagerAdapter: ImageSplitPagerAdapter? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings(this).ApplyTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityImageSplitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 全螢幕沉浸式：內容延伸至系統列後方，再隱藏狀態列與導覽列。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, binding.root)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        settings = AppSettings(this)
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        imageUrls = intent.getStringArrayListExtra(ExtraImageUrls)?.toList().orEmpty()
        if (imageUrls.isEmpty()) {
            // 相容舊的單一 URL 進入方式。
            val single = intent.getStringExtra(ExtraUrl)
            if (!single.isNullOrEmpty()) imageUrls = listOf(single)
        }
        imageIndex = intent.getIntExtra(ExtraPosition, 0)
        if (imageUrls.isNotEmpty()) {
            imageIndex = imageIndex.coerceIn(imageUrls.indices)
        }
        currentMode = when (intent.getStringExtra(ExtraMode)) {
            ModeTopBottom -> ModeTopBottom
            ModeNone -> ModeNone
            else -> ModeLeftRight
        }

        if (imageUrls.isEmpty()) {
            finish()
            return
        }

        binding.backButton.setOnClickListener { finish() }
        binding.prevImageButton.setOnClickListener { SwitchImage(-1) }
        binding.nextImageButton.setOnClickListener { SwitchImage(1) }
        binding.modeButton.setOnClickListener { ShowModeDialog() }
        binding.controlsToggleButton.setOnClickListener { HideControls() }
        binding.restoreControlsButton.setOnClickListener { ShowControls() }

        binding.splitPager.isUserInputEnabled = false
        binding.splitPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = UpdateCounterPill()
        })

        // 頂部控制與還原按鈕避開狀態列／瀏海，底部列避開手勢導覽列。
        WindowInsetsUtil.ApplyTopMarginInsets(binding.topControls, extraTopDp = 8)
        WindowInsetsUtil.ApplyTopMarginInsets(binding.restoreControlsButton, extraTopDp = 8)
        ApplyBottomInset()

        LoadCurrentImage()
    }

    private fun LoadCurrentImage(startAtLastPage: Boolean = false) {
        loadJob?.cancel()
        // 切換圖片時先清空舊分頁與解碼，避免殘留點陣圖與頁碼錯亂。
        pagerAdapter = null
        binding.splitPager.adapter = null
        imageFile = null
        imageWidth = 0
        imageHeight = 0
        contentGutters = emptyList()
        UpdateControls()

        loadJob = lifecycleScope.launch {
            ShowLoading(true)
            binding.messageText.visibility = View.GONE
            val url = imageUrls[imageIndex]
            // 繁體中文註解：Glide 的 lifecycle 綁定需在主執行緒建立，等待檔案與讀取尺寸才放到 IO。
            val future = Glide.with(this@ImageSplitActivity).asFile().load(url).submit()
            val resolved = withContext(Dispatchers.IO) { ResolveImage(future) }
            if (resolved == null) {
                ShowLoading(false)
                ShowMessage(getString(R.string.split_decode_failed))
                UpdateControls()
                return@launch
            }
            imageFile = resolved.file
            imageWidth = resolved.width
            imageHeight = resolved.height
            contentGutters = resolved.gutters
            // 指定頁數是依目前圖片比例記住的；換圖時用同一比例重算此圖頁數。
            if (currentMode == ModeTopBottom && manualPageCount != null && manualPageRatio != null) {
                manualPageCount = PagesForRatio(imageWidth, imageHeight)
            }
            awaitPagerLayout()
            val regions = ComputeRegions(currentMode)
            if (regions.isEmpty()) {
                ShowLoading(false)
                ShowMessage(getString(R.string.split_decode_failed))
                UpdateControls()
                return@launch
            }
            SetupPager(regions, startAtLastPage)
            ShowLoading(false)
            UpdateControls()
        }
    }

    private fun SetupPager(regions: List<Rect>, startAtLastPage: Boolean = false) {
        val file = imageFile ?: return
        val adapter = ImageSplitPagerAdapter(
            file = file,
            regions = regions,
            pagerWidth = binding.splitPager.width,
            maxScale = settings.imageZoomMax,
            onNavigate = { HandleNavigation(it) }
        )
        pagerAdapter = adapter
        binding.splitPager.adapter = adapter
        binding.splitPager.setCurrentItem(if (startAtLastPage) regions.lastIndex else 0, false)
        UpdateCounterPill()
    }

    // 模式按鈕改為開啟模式對話框，保留目前圖片並依選擇重算分頁。
    private fun ShowModeDialog() {
        val items = arrayOf(
            getString(R.string.split_left_right),
            getString(R.string.split_mode_top_auto),
            getString(R.string.split_mode_top_manual),
            getString(R.string.split_mode_none)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.split_mode_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        currentMode = ModeLeftRight
                        RecomputeAndSetup()
                    }
                    1 -> {
                        currentMode = ModeTopBottom
                        manualPageCount = null
                        RecomputeAndSetup()
                    }
                    2 -> ShowManualPagesDialog()
                    3 -> {
                        currentMode = ModeNone
                        RecomputeAndSetup()
                    }
                }
            }
            .show()
    }

    // 指定頁數對話框：預填目前比例換算的頁數或自動估算頁數；套用時僅接受 1..100。
    private fun ShowManualPagesDialog() {
        val dialogBinding = DialogSplitPagesBinding.inflate(layoutInflater)
        val prefill = when {
            imageWidth > 0 && imageHeight > 0 && manualPageRatio != null ->
                PagesForRatio(imageWidth, imageHeight)
            manualPageCount != null -> manualPageCount!!
            else -> EstimateAutoPageCount()
        }
        dialogBinding.pageCountEdit.setText(prefill.toString())
        dialogBinding.pageCountEdit.setSelection(dialogBinding.pageCountEdit.text?.length ?: 0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.split_manual_pages_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.split_apply) { _, _ ->
                val parsed = dialogBinding.pageCountEdit.text?.toString()?.trim()?.toIntOrNull()
                if (parsed == null || parsed < 1 || parsed > MaxPages) {
                    Toast.makeText(this, R.string.split_pages_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    manualPageCount = parsed
                    manualPageRatio = PageRatio(imageWidth, imageHeight, parsed)
                    currentMode = ModeTopBottom
                    RecomputeAndSetup()
                    Toast.makeText(this, R.string.split_ratio_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.split_cancel, null)
            .show()
    }

    private fun RecomputeAndSetup() {
        if (imageWidth > 0 && imageHeight > 0) {
            val regions = ComputeRegions(currentMode)
            if (regions.isNotEmpty()) SetupPager(regions)
        }
        UpdateControls()
    }

    // 目前自動（內容感知／平均）上下分割會產生的頁數，供指定頁數對話框預填。
    // 護欄判定不需分割時回傳 1，讓預填反映實際會顯示的單頁。
    private fun EstimateAutoPageCount(): Int {
        if (imageWidth <= 0 || imageHeight <= 0) return 1
        if (!NeedsTopBottomSplit(imageWidth, imageHeight)) return 1
        val ideal = IdealPageHeight()
        if (ideal <= 0) return 1
        return AutoTopBottomRegions(imageWidth, imageHeight, ideal).size.coerceAtLeast(1)
    }

    // 由指定頁數換算單頁比例（頁高 / 圖寬），供下一張需要分割的圖片沿用。
    private fun PageRatio(width: Int, height: Int, pages: Int): Float? {
        if (width <= 0 || height <= 0 || pages <= 0) return null
        return height.toFloat() / (pages.toFloat() * width.toFloat())
    }

    // 依記住的比例換算目前圖片頁數；微減誤差避免整數邊界被 ceil 多進一頁。
    private fun PagesForRatio(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return manualPageCount ?: 1
        val ratio = manualPageRatio ?: return manualPageCount ?: 1
        val pageHeight = ratio * width.toFloat()
        if (pageHeight <= 0f) return 1
        return ceil(height.toFloat() / pageHeight - 1e-4f)
            .toInt()
            .coerceIn(1, minOf(MaxPages, height))
    }

    private fun HideControls() {
        binding.topControls.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.restoreControlsButton.visibility = View.VISIBLE
        // 還原按鈕初始為 gone，需在顯示時重新分派 inset，才會套用與頂部控制相同的 top 偏移。
        ViewCompat.requestApplyInsets(binding.restoreControlsButton)
    }

    private fun ShowControls() {
        binding.topControls.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
        binding.restoreControlsButton.visibility = View.GONE
    }

    // 上一張／下一張：跳過不支援分割的格式；找不到則維持停用。
    private fun SwitchImage(delta: Int, startAtLastPage: Boolean = false) {
        val target = FindSplittableImage(imageIndex, delta)
        if (target < 0) return
        imageIndex = target
        LoadCurrentImage(startAtLastPage)
    }

    private fun FindSplittableImage(from: Int, delta: Int): Int {
        var index = from + delta
        while (index in imageUrls.indices) {
            if (IsSplittable(imageUrls[index])) return index
            index += delta
        }
        return -1
    }

    private fun ComputeRegions(mode: String): List<Rect> {
        val width = imageWidth
        val height = imageHeight
        if (width <= 0 || height <= 0) return emptyList()
        return when (mode) {
            ModeTopBottom -> {
                val idealPageHeight = IdealPageHeight()
                if (idealPageHeight <= 0) return emptyList()
                val manual = manualPageCount
                when {
                    // 自動護欄優先：比例正常就不分割，即使有指定頁數也不覆蓋。
                    !NeedsTopBottomSplit(width, height) -> listOf(Rect(0, 0, width, height))
                    // 指定頁數：使用者明確指定頁數時，等分為 N 頁。
                    manual != null -> ManualTopBottomRegions(width, height, manual)
                    // 自動模式：依內容感知 DP／平均分配。
                    else -> AutoTopBottomRegions(width, height, idealPageHeight)
                }
            }
            ModeNone -> listOf(Rect(0, 0, width, height))
            else -> {
                // 繁體中文註解：左右分割依右頁在前的閱讀順序呈現（先右再左）。
                // 護欄：非橫向跨頁（寬未達高 × 1.2）不左右切分，避免把一般直幅／方形圖對半切。
                if (width < 2 || !NeedsLeftRightSplit(width, height)) listOf(Rect(0, 0, width, height))
                else listOf(Rect(width / 2, 0, width, height), Rect(0, 0, width / 2, height))
            }
        }
    }

    // 左右分割護欄（一般比例門檻）：只有寬 > 高 × 1.2 的橫向跨頁才值得左右切分。
    private fun NeedsLeftRightSplit(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        return width > height * NoSplitWidthRatio
    }

    // 上下自動護欄（一般比例門檻）：縮放到 pager 寬度後，只有高 > pager 高 × 1.2 才需要上下分割。
    // pager 尚未 layout（尺寸為 0）時不阻擋，退回既有行為。
    private fun NeedsTopBottomSplit(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        val pagerWidth = binding.splitPager.width
        val pagerHeight = binding.splitPager.height
        if (pagerWidth <= 0 || pagerHeight <= 0) return true
        val scaledHeight = height.toFloat() * pagerWidth.toFloat() / width.toFloat()
        return scaledHeight > pagerHeight * NoSplitHeightRatio
    }

    // 上下頁分割的理想一頁高度（約等於一屏可顯示的原始像素高度）。
    private fun IdealPageHeight(): Int {
        val pagerWidth = binding.splitPager.width
        val pagerHeight = binding.splitPager.height
        if (pagerWidth <= 0 || pagerHeight <= 0 || imageWidth <= 0) return 0
        val scale = pagerWidth.toFloat() / imageWidth.toFloat()
        return ceil(pagerHeight / scale).toInt().coerceAtLeast(1)
    }

    // 自動上下頁：有留白帶走內容感知 DP，否則或 DP 無解時退回平均分配。
    private fun AutoTopBottomRegions(width: Int, height: Int, idealPageHeight: Int): List<Rect> {
        return if (contentGutters.isEmpty()) {
            EvenTopBottomRegions(width, height, idealPageHeight)
        } else {
            // 找不到可行路徑時退回平均分配，避免產生過大的單頁。
            ContentAwareRegions(width, height, idealPageHeight)
                .ifEmpty { EvenTopBottomRegions(width, height, idealPageHeight) }
        }
    }

    // 指定頁數：將圖片高度平均分成 exactly N 頁（N 夾在 1..100 與圖片高度內）。
    private fun ManualTopBottomRegions(width: Int, height: Int, requestedPages: Int): List<Rect> {
        val pages = requestedPages.coerceIn(1, minOf(MaxPages, height))
        val base = height / pages
        val remainder = height % pages
        val regions = ArrayList<Rect>(pages)
        var y = 0
        for (i in 0 until pages) {
            val pageHeight = base + if (i < remainder) 1 else 0
            regions.add(Rect(0, y, width, y + pageHeight))
            y += pageHeight
        }
        return regions
    }

    // 後備：無空白分隔帶時，將圖片高度平均分配到各頁（每頁相差至多 1px，總和剛好覆蓋全高）。
    private fun EvenTopBottomRegions(width: Int, height: Int, idealPageHeight: Int): List<Rect> {
        val pages = ceil(height.toFloat() / idealPageHeight.toFloat()).toInt().coerceIn(1, MaxPages)
        val base = height / pages
        val remainder = height % pages
        val regions = ArrayList<Rect>(pages)
        var y = 0
        for (i in 0 until pages) {
            val pageHeight = base + if (i < remainder) 1 else 0
            regions.add(Rect(0, y, width, y + pageHeight))
            y += pageHeight
        }
        return regions
    }

    // 內容感知：以動態規劃在「每頁高度介於 ideal 的 0.62–1.45 倍」限制下，挑選盡可能落在
    // 自然留白帶的分割路徑。留白使用量為主要目標，其次頁數較少、頁高偏離 ideal 較小、
    // 使用到的最窄留白較寬；因此三處寬留白可產生四頁，而非被貪心切成三頁。
    private fun ContentAwareRegions(width: Int, height: Int, idealPageHeight: Int): List<Rect> {
        if (height < 2) return listOf(Rect(0, 0, width, height))
        val minPage = (idealPageHeight * MinPageRatio).toInt().coerceAtLeast(1)
        val maxPage = (idealPageHeight * MaxPageRatio).toInt().coerceAtLeast(minPage + 1)

        // 候選切點：0、imageHeight，以及每個留白帶中心（限制在 1..height-1）；同一位置取最寬留白。
        val gutterHeightByPos = HashMap<Int, Int>()
        for (gutter in contentGutters) {
            val pos = gutter.center.coerceIn(1, height - 1)
            if (gutter.height > (gutterHeightByPos[pos] ?: 0)) gutterHeightByPos[pos] = gutter.height
        }
        val positions = (gutterHeightByPos.keys + 0 + height).sorted()
        val last = positions.size - 1
        fun gutterHeightAt(index: Int): Int =
            if (index == last) 0 else gutterHeightByPos[positions[index]] ?: 0

        // dp[j]：抵達候選 j 的最佳路徑；prev 記錄上游候選索引以供重建。
        val dp = arrayOfNulls<DpPath>(positions.size)
        dp[0] = DpPath(gutterUse = 0, pages = 0, deviation = 0, minGutter = Int.MAX_VALUE, prev = -1)
        for (j in 1..last) {
            for (i in 0 until j) {
                val from = dp[i] ?: continue
                val seg = positions[j] - positions[i]
                if (seg < minPage || seg > maxPage) continue
                val pages = from.pages + 1
                if (pages > MaxPages) continue
                val reward = gutterHeightAt(j)
                val candidate = DpPath(
                    gutterUse = from.gutterUse + reward,
                    pages = pages,
                    deviation = from.deviation + abs(seg - idealPageHeight),
                    minGutter = if (j == last) from.minGutter else minOf(from.minGutter, reward),
                    prev = i
                )
                val current = dp[j]
                if (current == null || candidate.IsBetterThan(current)) dp[j] = candidate
            }
        }

        if (dp[last] == null) return emptyList()
        val boundaries = ArrayList<Int>()
        var cursor = last
        while (cursor >= 0) {
            boundaries.add(positions[cursor])
            cursor = dp[cursor]?.prev ?: -1
        }
        boundaries.reverse()

        // 防禦性保護：末頁若短於 minPage 且至少有兩段內部邊界則併回前一頁。
        // DP 已限制每段 ≥ minPage，正常情況不會觸發。
        if (boundaries.size >= 3) {
            val lastStart = boundaries[boundaries.size - 2]
            val lastEnd = boundaries[boundaries.size - 1]
            if (lastEnd - lastStart < minPage) boundaries.removeAt(boundaries.size - 2)
        }

        val regions = ArrayList<Rect>(boundaries.size - 1)
        for (i in 0 until boundaries.size - 1) {
            regions.add(Rect(0, boundaries[i], width, boundaries[i + 1]))
        }
        return regions
    }

    private fun UpdateControls() {
        binding.modeButton.text = ModeButtonLabel()
        binding.prevImageButton.isEnabled = FindSplittableImage(imageIndex, -1) >= 0
        binding.nextImageButton.isEnabled = FindSplittableImage(imageIndex, 1) >= 0
        UpdateCounterPill()
    }

    // 模式按鈕短標籤：左右 / 上下自動 / 上下 N 頁 / 還原。
    private fun ModeButtonLabel(): String = when (currentMode) {
        ModeLeftRight -> getString(R.string.split_mode_lr_short)
        ModeNone -> getString(R.string.split_mode_none_short)
        else -> {
            val manual = manualPageCount
            if (manual != null) getString(R.string.split_mode_tb_manual_short, manual)
            else getString(R.string.split_mode_tb_auto_short)
        }
    }

    private fun UpdateCounterPill() {
        val total = pagerAdapter?.itemCount ?: 0
        binding.counterPill.text = if (total > 0) {
            val pill = getString(
                R.string.split_counter_pill,
                imageIndex + 1,
                imageUrls.size,
                binding.splitPager.currentItem + 1,
                total
            )
            val suffix = when (currentMode) {
                ModeNone -> ""
                ModeLeftRight ->
                    // 左右模式護欄阻擋時提示不需分割，否則不加後綴。
                    if (NeedsLeftRightSplit(imageWidth, imageHeight)) ""
                    else "（${getString(R.string.split_not_needed)}）"
                else -> {
                    // 上下頁：先判斷比例是否需要分割；比例正常時即使有指定頁數也不分割。
                    when {
                        !NeedsTopBottomSplit(imageWidth, imageHeight) ->
                            "（${getString(R.string.split_not_needed)}）"
                        manualPageCount != null ->
                            "（${getString(R.string.split_ratio_suffix)}）"
                        else ->
                            "（${getString(R.string.split_content)}）"
                    }
                }
            }
            pill + suffix
        } else {
            getString(R.string.split_image_counter, imageIndex + 1, imageUrls.size)
        }
    }

    private fun MovePage(delta: Int) {
        val total = pagerAdapter?.itemCount ?: 0
        if (total == 0) return
        val next = binding.splitPager.currentItem + delta
        if (next in 0 until total) {
            binding.splitPager.setCurrentItem(next, true)
        } else {
            // 越界時切換圖片：往下一張從第 0 頁開始，往上一張跳到最後一頁。
            SwitchImage(delta, startAtLastPage = delta < 0)
        }
    }

    private fun HandleNavigation(navigation: ZoomImageView.ImageNavigation) {
        when (navigation) {
            ZoomImageView.ImageNavigation.Next -> MovePage(1)
            ZoomImageView.ImageNavigation.Previous -> MovePage(-1)
            ZoomImageView.ImageNavigation.RightSwipePrevious -> MovePage(-1)
            ZoomImageView.ImageNavigation.Exit -> finish()
        }
    }

    private fun ShowLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun ShowMessage(text: String) {
        binding.messageText.text = text
        binding.messageText.visibility = View.VISIBLE
    }

    // 底部列加上導覽列 inset，避免按鈕被手勢列遮蓋。
    private fun ApplyBottomInset() {
        val basePaddingBottom = binding.bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { view, insets ->
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = basePaddingBottom + navigationBar)
            insets
        }
    }

    // 上下頁分割需要 pager 實際尺寸；尚未 layout 時等待一次。
    private suspend fun awaitPagerLayout() = suspendCancellableCoroutine<Unit> { cont ->
        if (binding.splitPager.width > 0 && binding.splitPager.height > 0) {
            if (cont.isActive) cont.resume(Unit)
        } else {
            binding.splitPager.doOnLayout { if (cont.isActive) cont.resume(Unit) }
        }
    }

    private fun ResolveImage(future: Future<File>): ResolvedImage? {
        return try {
            val file = future.get() ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            // 無論目前模式為何，載入時一併分析空白分隔帶，之後切到上下頁分割即可立即使用。
            val gutters = ImageContentAnalyzer.Analyze(file, options.outWidth, options.outHeight)
            ResolvedImage(file, options.outWidth, options.outHeight, gutters)
        } catch (e: Exception) {
            null
        }
    }

    // 動畫/影格格式無法以 BitmapRegionDecoder 分割，僅接受靜態圖。
    private fun IsSplittable(imageUrl: String): Boolean {
        val extension = imageUrl.substringAfterLast('.', "").lowercase().substringBefore('?')
        return extension !in UnsupportedSplitExtensions
    }

    private data class ResolvedImage(
        val file: File,
        val width: Int,
        val height: Int,
        val gutters: List<ImageContentAnalyzer.Gutter>
    )

    // 內容感知 DP 的單一候選路徑評分；IsBetterThan 定義偏好順序。
    private class DpPath(
        val gutterUse: Int,
        val pages: Int,
        val deviation: Int,
        val minGutter: Int,
        val prev: Int
    ) {
        // 偏好順序：留白使用量最大 → 頁數較少 → 頁高偏離 ideal 較小 → 使用到的最窄留白較寬。
        fun IsBetterThan(other: DpPath): Boolean {
            if (gutterUse != other.gutterUse) return gutterUse > other.gutterUse
            if (pages != other.pages) return pages < other.pages
            if (deviation != other.deviation) return deviation < other.deviation
            if (minGutter != other.minGutter) return minGutter > other.minGutter
            return false
        }
    }

    companion object {
        const val ExtraImageUrls = "split_image_urls"
        const val ExtraPosition = "split_position"
        const val ExtraUrl = "split_url"
        const val ExtraMode = "split_mode"
        const val ModeLeftRight = "left_right"
        const val ModeTopBottom = "top_bottom"
        const val ModeNone = "none"
        private const val MaxPages = 100
        private const val MinPageRatio = 0.62f
        private const val MaxPageRatio = 1.45f
        // 分割護欄的一般比例門檻：左右需寬 > 高 × 1.2；上下自動需縮放後高 > 一屏高 × 1.2。
        private const val NoSplitWidthRatio = 1.2f
        private const val NoSplitHeightRatio = 1.2f
        private val UnsupportedSplitExtensions = setOf("gif", "webm", "mp4")
    }
}
