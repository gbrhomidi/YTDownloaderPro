
#!/bin/bash
echo "📦 Building YouTube Downloader Pro..."
if [ ! -f "app/src/main/assets/yt-dlp" ]; then
    echo "⚠️  Downloading yt-dlp..."
    wget -O app/src/main/assets/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp
    chmod +x app/src/main/assets/yt-dlp
fi
gradle assembleDebug
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "✅ APK ready: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Build failed"
fi

