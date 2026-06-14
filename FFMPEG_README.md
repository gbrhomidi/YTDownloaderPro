# FFmpeg Setup for YTDownloaderPro

## What is FFmpeg?
FFmpeg is required ONLY for video compression feature.
The app works perfectly WITHOUT FFmpeg for downloading videos.

## Download Methods

### Method 1: Termux (Easiest)
```bash
pkg install ffmpeg
cp $(which ffmpeg) app/src/main/assets/ffmpeg
chmod +x app/src/main/assets/ffmpeg
```

### Method 2: Pre-built Binary
```bash
# For ARM64 (most modern phones)
wget -O app/src/main/assets/ffmpeg   https://github.com/termux/termux-packages/releases/download/bootstrap/bootstrap-aarch64.zip
# Extract ffmpeg from the zip

chmod +x app/src/main/assets/ffmpeg
```

### Method 3: Skip FFmpeg
The app will automatically detect if FFmpeg is missing and disable compression.
All other features (download, audio extraction) work perfectly without it.

## Verification
```bash
app/src/main/assets/ffmpeg -version
```
