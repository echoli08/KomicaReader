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
4. **主題篩選**：可在列表頁透過關鍵字快速篩選已載入的主題
5. **快速分享**：支援直接從列表分享主題連結給其他應用程式
6. **串列詳情**：點擊串列進入詳情頁面，檢視所有回應內容和完整的討論記錄
7. **引文預覽**：長按引文編號 (>>No.) 可快速預覽該篇回應內容，放開即消失，無需跳轉頁面
8. **圖片預覽**：支援全螢幕圖片檢視，可透過滑動切換瀏覽串列中的多張圖片，並優化縮圖顯示大小
9. **響應式介面**：採用 Material Design 風格，提供流暢且直觀的使用者體驗

## 更新日誌

### V1.03 (2025-12-31)
- **新增功能**:
    - **超連結支援**: 文章內的網址（http/https）現在可以點擊並直接使用預設瀏覽器開啟。
- **UI 與體驗優化**:
    - **引文偵測強化**: 統一優化了引文編號（>>No.）的偵測與染色邏輯，解決了重疊連結導致的點擊無反應問題。
    - **引文邏輯修正**: 僅對目前清單中存在的回應編號進行連結染色，提升介面直覺性。
- **維護與修正**:
    - 修正了 PostAdapter 與 ThreadDetailActivity 中的多項語法錯誤與重複定義問題。
    - 強化了底層網路請求的執行緒安全性。

### V1.02 (2025-12-31)
- **UI 優化**:
    - 將討論串詳情頁面的標題字體大小由 9sp 調整為 14sp，提升閱讀舒適度。
- **維護與修正**:
    - 修復了 ThreadDetailActivity 在部分環境下因 lambda 表達式導致的編譯失敗。
    - 清理冗餘程式碼，維持核心功能的穩定性。

### V1.0.1 (2025-12-30)
- **新增功能**:
    - **引文預覽**: 長按引文編號 (>>No.) 可快速預覽該篇回應內容，放開即消失。
    - **我的最愛**: 可將常逛的看板加入最愛並置頂。
    - **主題篩選**: 列表頁新增「篩選」按鈕與關鍵字過濾功能。
    - **快速分享**: 列表頁新增分享按鈕。
    - **UI 優化**: 顯示回覆總數、優化縮圖顯示大小。
- **技術調整**:
    - 升級至 Java 17。
    - 更新 App Icon。
    - 修復建置錯誤與資源遺失。

## Requirements

- Android SDK 21 (Android 5.0) or higher
- Target SDK 34 (Android 14)

## Technologies

- Kotlin/Java
- OkHttp for HTTP requests
- Jsoup for HTML parsing
- Gson for JSON parsing
- Glide for image loading
- Material Design Components
- ViewPager2 for image navigation

## Building

```bash
./gradlew assembleDebug
```

Or on Windows:

```bash
gradlew.bat assembleDebug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

## Installation

1. Enable "Unknown sources" in your device security settings
2. Install the APK file
3. Grant internet permission when prompted

## License

This project is for educational purposes only.
