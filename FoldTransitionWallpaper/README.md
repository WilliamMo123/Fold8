# Fold Transition Wallpaper

在 Galaxy Z Fold 8 上,依鉸鏈開合角度即時混合「外螢幕」與「內螢幕」兩張桌布圖片,
並用 AGSL shader 疊加一道玻璃掃光效果,模擬 iPhone Duo 開合過場的視覺質感。

原理與國外玩家在 Reddit 上示範的做法一致:
1. 讀取 `Sensor.TYPE_HINGE_ANGLE`(鉸鏈角度感測器,單位為度,0° = 全折疊,180° = 完全攤平)
2. 用 `android.graphics.RuntimeShader`(AGSL)依角度即時內插兩張截圖
3. 包成 `WallpaperService`(動態桌布),套用到系統桌布

## 需求

- Android Studio(建議 Koala 以上版本)
- 一台 **實體** Galaxy Z Fold 8(或其他有 `TYPE_HINGE_ANGLE` 感測器的可摺疊手機)
  - 模擬器沒有鉸鏈感測器,程式會退回「來回擺動」的示範動畫,只是為了讓你在一般手機上也能確認 shader 效果本身有沒有跑對,不是真的鉸鏈互動
- minSdk 33(RuntimeShader 與鉸鏈角度感測器都需要 Android 13+)

## 關於「打包好的 APK」

我目前的執行環境沒有網路權限可以下載 Google 的 Android SDK / Gradle 套件庫(只能存取
GitHub、npm、PyPI 等有限網域),所以**沒辦法在這裡直接幫你編出 apk 檔**。這不是我不想做,
是環境限制。以下是取得 apk 最快的兩條路,任選一種:

### 方法 A:GitHub Actions 自動編譯(不需要裝 Android Studio)

專案裡已經放了 `.github/workflows/build-apk.yml`。

1. 把這個資料夾整個推上你自己的 GitHub repo(可以是 private)
2. 推上去之後,GitHub 會自動開始編譯(或到 repo 的 Actions 分頁手動按 "Run workflow")
3. 編譯完成後,進 Actions → 該次執行紀錄 → 下方 "Artifacts" 區塊,下載
   `FoldTransitionWallpaper-debug-apk.zip`,解壓縮就是 `app-debug.apk`
4. 把 apk 傳到手機(例如用 Google Drive、或用 USB 傳檔),在手機上開啟安裝
   (需先在「設定 → 安全性」允許安裝不明來源的 App)

### 方法 B:Android Studio 一鍵編譯

1. 開啟 Android Studio → Open → 選這個資料夾
2. 等 Gradle 同步完(第一次會自動下載 wrapper,需要網路)
3. 上方選單 Build → Build App Bundle(s) / APK(s) → Build APK(s)
4. 編完後右下角會跳出 "locate" 連結,點進去就能找到 `app-debug.apk`,直接傳到手機安裝即可

兩種方法產生的都是**debug 版 apk**,足夠你自己側載安裝測試用。

## 這次新增的功能

- **設定畫面加入即時預覽**:在套用桌布前,可以先在畫面上方看到即時渲染的過場效果,並用底下的
  滑桿模擬鉸鏈角度(0 = 完全折疊、100 = 完全攤平),概念上對應 iPhone Duo 設定頁「選照片 +
  預覽效果」的體驗。
- **重新選擇圖片按鈕**:可以清除目前選好的兩張圖,重新挑選。
- 這個設定畫面本來就會出現在「長按桌布 → 自訂」裡(因為在 `wallpaper.xml` 有指定
  `settingsActivity`),套用桌布後隨時可以回來調整。

## 安裝步驟

1. 用 Android Studio 開啟這個資料夾(File → Open)
2. 等 Gradle 同步完成,直接 Run 到你的 Z Fold 8 上(需開啟開發者模式 + USB 偵錯)
3. App 打開後:
   - 分別選「選擇外螢幕截圖」「選擇內螢幕截圖」(建議先自己截圖,外螢幕桌面 + 內螢幕桌面各一張)
   - 按「套用為動態桌布」,系統會跳到桌布設定頁
   - 選擇「摺疊開合過渡桌布」→ 套用
4. 回到桌面,實際開合手機,觀察桌布是否隨角度變化並出現掃光效果

## 已知限制(跟原始 Reddit demo 一樣)

- 三星沒有開放第三方 App 深度存取 System UI,所以這個效果**只作用在桌布層**,
  沒辦法像蘋果官方系統動畫一樣,連 App 切換、Launcher 圖示排列一起做開合過場。
- `RuntimeShader` 中兩張圖是用 `Bitmap.createScaledBitmap` 直接拉伸鋪滿畫面,
  沒有做裁切對齊,如果兩張截圖比例不同,可能會變形,可以之後再優化成 centerCrop。
- 目前效果是「左右方向的雨刷內插 + 掃光」,如果你想要更接近 iPhone Duo 的「畫面往內收合、
  帶一點透視/景深」的效果,可以在 `FoldWallpaperService.AGSL_SOURCE` 那段 shader 程式碼上調整,
  例如加入 `uv` 的透視變形或模糊過渡,我可以再幫你加。

## 檔案位置

- `app/src/main/java/com/wmo/foldwallpaper/FoldWallpaperService.kt` — 核心邏輯(感測器 + shader)
- `app/src/main/java/com/wmo/foldwallpaper/MainActivity.kt` — 選圖 + 套用桌布的畫面
- `app/src/main/java/com/wmo/foldwallpaper/WallpaperImageStore.kt` — 圖片存取共用工具
