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
2. **串列檢視**：在選定的看板中檢視串列，每個串列顯示預覽縮圖和摘要資訊
3. **串列詳情**：點擊串列進入詳情頁面，檢視所有回應內容和完整的討論記錄
4. **圖片預覽**：支援全螢幕圖片檢視，可透過滑動切換瀏覽串列中的多張圖片
5. **響應式介面**：採用 Material Design 風格，提供流暢且直觀的使用者體驗

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
