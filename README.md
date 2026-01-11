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
9. **圖片輪播**：支援自動輪播與播放/暫停控制，可設定輪播秒數
10. **螢幕常亮**：可設定輪播或預覽時保持螢幕不休眠
11. **圖片分享**：長按圖片可直接叫用系統分享選單，快速分享圖片至其他應用程式
12. **響應式介面**：採用 Material Design 3 風格，支援日夜模式切換，提供流暢的閱讀體驗
13. **快取與重新整理**：支援磁碟與記憶體快取，並可透過下拉動作手動重新整理最新內容
14. **瀏覽紀錄**：自動記錄瀏覽過的討論串，支援從主頁與列表進入回顧，並提供一鍵清除功能
15. **圖片牆模式**：在討論串詳情中可切換至圖片牆模式，以網格方式快速瀏覽所有圖片
16. **回覆 WebView**：回覆改採 WebView，並可在設定中開啟「回覆頁資源精簡」加速載入

## 截圖預覽

<p align="center">
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101029_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101032_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101039_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101049_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260102_101054_Komica_Reader.jpg" height="400" />
  <img src="https://raw.githubusercontent.com/echoli08/KomicaReader/refs/heads/master/ScreenShot/Screenshot_20260105_094601_Komica_Reader.jpg" height="400" />
</p>

## 更新日誌

### V1.26.0111 (2026-01-11) - 版本號更新
- **版本號**: 同步更新 App 版本與說明文件。

### V1.26.0109 (2026-01-09) - 現代化架構重構 (重大更新)
- **架構優化 (Architecture)**:
    - **Hilt 依賴注入**: 導入 Google 推薦的 Hilt 框架，全面取代手動管理的 Singleton，提升程式碼模組化與可測試性。
    - **ViewBinding 全面導入**: 在所有 Activity 中啟用 ViewBinding，移除 `findViewById`，提供編譯期型別安全，徹底杜絕 NullPointer 崩潰風險。
    - **錯誤狀態管理 (Resource State)**: 實作 `Resource<T>` Sealed Class，讓 UI 能精確區分「載入中」、「載入成功」與「網路/系統錯誤」狀態，並提供友好的錯誤提示。
- **Kotlin 遷移 (Migration)**:
    - 完成核心 Activity (`Main`, `ThreadList`, `ThreadDetail`, `Gallery`) 的 Kotlin 遷移。
    - 統一非同步處理模式，移除不安全的 `GlobalScope` 與 `ExecutorService`，改用結構化協程 (`lifecycleScope`, `viewModelScope`)。
- **效能與穩定性 (Performance & Stability)**:
    - **資料庫優化**: 為 Room `history` 資料表加入 `url` 索引，大幅提升高資料量下的查詢效能。
    - **Lifecycle 升級**: `KomicaReaderApplication` 遷移至 `DefaultLifecycleObserver`，符合 Android 最新 API 標準。
- **回覆體驗 (Reply)**:
    - **WebView 回覆**: 回覆改用 WebView 手動送出，並新增「回覆頁資源精簡」設定加速載入。
- **建置 (Build)**:
    - **Gradle 指派語法更新**: 修正空白指派語法的 deprecation，僅剩 Kapt 外掛內部警告需待升級處理。
- **測試 (Testing)**:
    - 建立 `MainViewModelTest` 單元測試，引入 **MockK** 與 **Coroutines Test** 框架，確保核心業務邏輯的穩定性。

### V1.26.0106 (2026-01-06) - 架構升級與新功能
- **新功能**: 實作本地資料庫 (Room) 紀錄瀏覽歷程，新增圖片牆模式。
- **架構**: 全面導入 Kotlin 支援，引入 Coroutines 處理非同步任務。

... (其餘舊日誌保留)

## 系統需求

- Android SDK 24 (Android 7.0) 或更高版本
- Target SDK 34 (Android 14)

## 技術棧 (Tech Stack)

- **Language**: Kotlin (Primary), Java (Legacy)
- **DI**: Hilt (Dagger)
- **Architecture**: MVVM + Repository Pattern
- **Async**: Kotlin Coroutines & Flow
- **UI Binding**: ViewBinding
- **Networking**: OkHttp 4
- **Parsing**: Jsoup
- **Image Loading**: Glide
- **Database**: Room
- **UI**: Material Design 3, SwipeRefreshLayout

## 編譯方法

```bash
./gradlew assembleDebug
```

編譯產出的 APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。

## 授權

本專案僅供學術研究與個人使用。

