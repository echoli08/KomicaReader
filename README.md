# Komica Reader

<br>因K島實在是找不到簡單實用的APP，以前常用的也都不能用了，
<br>基於研究興趣利用Codex以及OpenCode作出來的K島閱讀器。
<br>目前功能基本上應該都已完整(?)，暫時還想不出還能做什麼功能
<br>主題搜尋功能暫時擱置

A native Android application for browsing Komica anonymous discussion boards.

## App 功能簡介

Komica Reader 是一款專為瀏覽 Komica 匿名討論板設計的 Android 應用程式。應用程式提供以下主要功能：

1. **看板瀏覽**：顯示多個看板分類（如 /all/、/cat/ 等），使用者可選擇感興趣的看板進入瀏覽
2. **我的最愛**：可將常逛的看板加入最愛，並自動置頂於列表中方便快速存取
3. **串列檢視**：在選定的看板中檢視串列，每個串列顯示預覽縮圖、摘要資訊與回覆總數
4. **快速分享**：支援直接從列表分享主題連結給其他應用程式
5. **串列詳情**：點擊串列進入詳情頁面，檢視所有回應內容和完整的討論記錄
6. **引文預覽**：長按引文編號 (>>No.) 可快速預覽該篇回應內容，放開即消失，無需跳轉頁面
7. **圖片預覽**：支援全螢幕圖片檢視，可透過滑動切換瀏覽串列中的多張圖片，支援雙指縮放/拖曳並可設定最大放大倍率
8. **圖片輪播**：支援自動輪播與播放/暫停控制，可設定輪播秒數
9.  **螢幕常亮**：可設定輪播或預覽時保持螢幕不休眠
10. **圖片操作**：長按圖片可分享或設成桌布，圖片牆長按可下載到公開相簿或分享
11. **下載路徑設定**：可在設定中切換下載到公開圖片/下載資料夾（相簿可見）
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

### V1.26.0112 (2026-01-12) - 圖片縮放與設定
- **功能更新**:
    - **圖片預覽**: 新增雙指縮放/拖曳，放大時暫停翻頁避免誤觸。
    - **縮放上限**: 設定頁新增「預覽放大上限」，可選 2/3/4/5 倍。

### V1.26.0111 (2026-01-11) - 版本號更新
- **版本號**: 同步更新 App 版本與說明文件。
- **功能更新**:
    - **圖片輪播**: 新增播放/暫停與輪播秒速調整，預設不自動啟動。
    - **圖片循環**: 圖片預覽支援循環切換。
    - **螢幕常亮**: 可分別設定輪播/預覽時是否保持常亮。
    - **圖片牆操作**: 圖片牆長按可選擇下載或分享圖片。
    - **圖片預覽操作**: 長按圖片支援下載、分享與設成桌布。
    - **我的最愛備份**: 新增匯出/匯入功能，可合併或覆蓋還原。
    - **下載路徑**: 下載改寫入公開相簿，並可切換公開圖片/下載資料夾。
    - **介面微調**: 設定頁改為可捲動，關於本程式資訊可正常顯示。
    - **控制按鈕**: 圖片牆輪播播放/速度圖示風格統一。
    - **效能優化**: 圖片載入加入尺寸限制、引文解析快取與字體設定預讀。

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
