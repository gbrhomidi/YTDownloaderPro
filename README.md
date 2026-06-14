
# 📥 YouTube Downloader Pro - Android Project

## هيكل المشروع

```
YTDownloaderPro/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── index.html
│   │   │   ├── yt-dlp
│   │   │   └── ffmpeg
│   │   ├── java/com/ytdownloader/pro/
│   │   │   └── MainActivity.java
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml
│   │   │   ├── values/
│   │   │   │   └── strings.xml
│   │   │   └── xml/
│   │   │       ├── network_security_config.xml
│   │   │       └── file_paths.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## ⚡ المتطلبات

1. **yt-dlp binary** للـ Android ARM64
   - حمل من: https://github.com/yt-dlp/yt-dlp/releases
   - ضعه في: `app/src/main/assets/yt-dlp`

2. **FFmpeg binary** (اختياري)
   - حمل من Termux: `pkg install ffmpeg`
   - ضعه في: `app/src/main/assets/ffmpeg`

## 🔧 بناء APK

```bash
cd YTDownloaderPro
./gradlew assembleDebug
```

## 📱 التثبيت
```bash
pm install app/build/outputs/apk/debug/app-debug.apk
```

