# Komica Reader


<br>因K島實在是找不到簡單實用的APP，以前常用的也都不能用了，
<br>導致在手機上只能透過網頁看K島，
<br>於是我基於研究興趣利用Codex以及OpenCode作出來的K島閱讀器，
<br>接下來會嘗試用Gemini來接續後續處理，
<br>另外雖然會寫cobol跟c#但我對於java以及android其實是一竅不通的，
<br>所以有問題我也會繼續透過這方式來實作跟修正，翻車機率很高。


A native Android application for browsing Komica anonymous discussion boards.

## App 功能簡介

Komica Reader 是一款專為瀏覽 Komica 匿名討論板設計的 Android 應用程式。應用程式提供以下主要功能：

1. **看板瀏覽**：顯示多個看板分類（如 /all/、/cat/ 等），使用者可選擇感興趣的看板進入瀏覽
2. **我的最愛**：可將常逛的看板加入最愛，並自動置頂於列表中方便快速存取
3. **串列檢視**：在選定的看板中檢視串列，每個串列顯示預覽縮圖、摘要資訊與回覆總數
4. **主題篩選**：支援本地關鍵字篩選與遠端伺服器搜尋，快速定位感興趣的討論
5. **快速分享**：支援直接從列表分享主題連結給其他應用程式
6. **串列詳情**：點擊串列進入詳情頁面，檢視所有回應內容和完整的討論記錄
7. **引文預覽**：長按引文編號 (>>No.) 可快速預覽該篇回應內容，放開即消失，無需跳轉頁面
8. **圖片預覽**：支援全螢幕圖片檢視，可透過滑動切換瀏覽串列中的多張圖片，並優化縮圖顯示大小
9. **圖片分享**：長按圖片可直接叫用系統分享選單，快速分享圖片至其他應用程式
10. **響應式介面**：採用 Material Design 風格，支援日夜模式切換，提供流暢的閱讀體驗
11. **快取與重新整理**：支援磁碟與記憶體快取，並可透過下拉動作手動重新整理最新內容

## 截圖預覽

<p align="center">
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101029_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101032_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101039_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101049_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101054_Komica_Reader.jpg" height="400" />
</p>

## 更新日誌

### V1.26.0105 (2026-01-05) - 介面美化與自訂功能
- **介面與體驗 (UI/UX)**:
    - **懸浮按鈕 (FAB)**: 討論串詳情頁改用右下角懸浮按鈕進行「跳到底部」與「分享」，釋放底部閱讀空間。
    - **標題置頂優化**: 將討論串標題整合至頂部 Toolbar，與返回鍵對齊，並固定顯示不隨捲動隱藏。
    - **緊湊標題列**: 將全域標題列高度縮小為 48dp，增加內容顯示區域。
    - **視覺一致性**:
        - 討論串內文改用 CardView 樣式，增加圓角與陰影，並優化了深色模式下的對比度。
        - 列表頁的分享按鈕改為圓形 FAB 風格，與詳情頁保持一致。
        - 統一了詳情頁懸浮按鈕的顏色與陰影樣式，消除視覺突兀感。
- **功能調整**:
    - **移除排序**: 移除複雜的排序功能，統一採用「發文時間倒序」排列，還原最直覺的閱讀習慣。
    - **字體大小設定**: 新增字體大小調整功能，可獨立設定「主題列表」與「討論串內文」的文字大小。
    - **即時預覽**: 設定變更後會立即套用至當前頁面，無需重啟 App。

### V1.26.0104 (2026-01-04) - 搜尋與核心修復
- **搜尋功能修復**:
    - **Gaia/新番看板支援**: 針對「新番捏他」、「新番實況」等使用舊版 Pixmicat 腳本的板塊，實作了專屬的 Big5 編碼轉換與 POST 請求邏輯，解決了搜尋關鍵字亂碼與無回應的問題。
    - **解析邏輯增強**: 優化了搜尋結果頁面的 HTML 解析器，能正確識別並抓取舊式表格佈局 (`table`/`td`) 中的內容。
    - **錯誤處理**: 修正了編碼偵測邏輯，並加入了針對「搜尋無結果」的明確判斷，避免誤抓看板首頁內容。
- **穩定性與編譯**:
    - 修正了 OkHttp `RequestBody` 與 `FormBody` 的混用問題。
    - 補齊了遺漏的 `KLog` 與正則表達式類別匯入，解決編譯錯誤。

### V1.1.0 (2026-01-02) - 重大架構更新
- **架構重構 (MVVM)**:
    - 全面導入 **MVVM (Model-ViewModel-ViewModel)** 架構，解決 Activity 過於臃腫的問題。
    - 引入 **LiveData** 管理 UI 狀態，徹底解決螢幕旋轉導致的資料丟失與載入狀態卡死問題。
    - 建立 **Repository 模式**，統一管理網路請求、快取與資料流。
- **效能與最佳化**:
    - **多級快取機制**: 實作了 OkHttp 磁碟快取與 LruCache 記憶體快取，大幅縮短重複開啟討論串的載入時間。
    - **列表流暢度**: 使用 **DiffUtil** 優化 RecyclerView 更新，讓搜尋過濾與分頁載入更平滑。
    - **網路穩定性**: 加入網路連線逾時處理，防止在不穩定網路下介面無止盡等待。
- **功能增強**:
    - **遠端搜尋**: 實作伺服器端搜尋功能，搜尋不再侷限於已載入的本地內容。
    - **下拉重新整理**: 在看板列表與討論串列表加入 **SwipeRefreshLayout**，支援手動強制更新。
- **安全性加固**:
    - **網路安全配置**: 實作 `network_security_config.xml`，精確控制明文流量 (HTTP) 權限，強化隱私防護。
- **穩定性與修正**:
    - 修正了多處執行緒洩漏 (Thread Leak) 與記憶體管理問題。
    - 優化了 URL 分頁解析邏輯，相容更多不同類型的 K 島板塊。

### V1.0.5 (2026-01-02)
- **介面與體驗優化**:
    - **夜間模式完善**:
        - 調整標題列 (App Bar) 與狀態列顏色，在夜間模式下使用深灰色與黑色，風格更統一。
        - 修正引文預覽視窗的標題配色，解決深色背景下文字無法辨識的問題。
        - 優化點擊引文時的高亮顏色，夜間模式改用深棕色，確保文字清晰可見且不刺眼。
    - **篩選區塊美化**:
        - 重新設計篩選輸入框，加入圓角與邊框樣式。
        - 篩選與排序按鈕改用主題色背景與白色文字，並加入按壓漣漪效果，提升操作質感。

... (其餘舊日誌保留)

## Requirements

- Android SDK 21 (Android 5.0) or higher
- Target SDK 34 (Android 14)

## Technologies

- Kotlin/Java
- **Android Architecture Components (ViewModel, LiveData)**
- OkHttp for HTTP requests (with Caching)
- Jsoup for HTML parsing
- Glide for image loading
- **SwipeRefreshLayout**
- Material Design Components

## Building

```bash
./gradlew assembleDebug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

## License

This project is for educational purposes only.