package com.ytdownloader.pro;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "YTDownloader";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private WebView webView;
    private ExecutorService executor;
    private Handler mainHandler;
    private Map<String, DownloadTask> tasks = new HashMap<>();
    private File ytDlpBinary;
    private File ffmpegBinary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newFixedThreadPool(3);
        mainHandler = new Handler(Looper.getMainLooper());

        checkPermissions();
        setupWebView();
        extractBinaries();
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.INTERNET);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void setupWebView() {
        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new YtDlpBridge(), "Android");

        // Load from assets
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void extractBinaries() {
        executor.execute(() -> {
            try {
                File binDir = new File(getFilesDir(), "bin");
                if (!binDir.exists()) binDir.mkdirs();

                ytDlpBinary = new File(binDir, "yt-dlp");
                ffmpegBinary = new File(binDir, "ffmpeg");

                // Extract yt-dlp from assets (you need to add yt-dlp binary to assets)
                if (!ytDlpBinary.exists()) {
                    extractAsset("yt-dlp", ytDlpBinary);
                    ytDlpBinary.setExecutable(true);
                }

                // Extract ffmpeg from assets
                if (!ffmpegBinary.exists()) {
                    extractAsset("ffmpeg", ffmpegBinary);
                    ffmpegBinary.setExecutable(true);
                }

                Log.d(TAG, "Binaries extracted successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error extracting binaries: " + e.getMessage());
            }
        });
    }

    private void extractAsset(String assetName, File destFile) throws IOException {
        InputStream is = getAssets().open(assetName);
        FileOutputStream fos = new FileOutputStream(destFile);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
        }
        fos.close();
        is.close();
    }

    public class YtDlpBridge {
        @JavascriptInterface
        public String downloadVideo(String url, String path, String options) {
            try {
                String taskId = UUID.randomUUID().toString();
                DownloadTask task = new DownloadTask(taskId, url, path, options);
                tasks.put(taskId, task);

                executor.execute(() -> {
                    try {
                        task.status = "downloading";
                        runYtDlp(task);
                    } catch (Exception e) {
                        task.status = "error";
                        task.error = e.getMessage();
                        Log.e(TAG, "Download error: " + e.getMessage());
                    }
                });

                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("taskId", taskId);
                return result.toString();
            } catch (Exception e) {
                return createError(e.getMessage());
            }
        }

        @JavascriptInterface
        public String cancelDownload(String taskId) {
            DownloadTask task = tasks.get(taskId);
            if (task != null) {
                task.cancelled = true;
                task.status = "cancelled";
                if (task.process != null) {
                    task.process.destroyForcibly();
                }
            }
            JSONObject result = new JSONObject();
            try {
                result.put("success", true);
            } catch (JSONException e) {}
            return result.toString();
        }

        @JavascriptInterface
        public String getProgress(String taskId) {
            DownloadTask task = tasks.get(taskId);
            if (task == null) {
                return createError("Task not found");
            }
            try {
                JSONObject result = new JSONObject();
                result.put("status", task.status);
                result.put("percent", task.percent);
                result.put("speed", task.speed);
                result.put("eta", task.eta);
                result.put("title", task.title);
                result.put("filesize", task.filesize);
                result.put("duration", task.duration);
                result.put("error", task.error);

                JSONArray files = new JSONArray();
                if (task.outputFile != null && task.outputFile.exists()) {
                    JSONObject file = new JSONObject();
                    file.put("name", task.outputFile.getName());
                    file.put("path", task.outputFile.getAbsolutePath());
                    file.put("size", formatSize(task.outputFile.length()));
                    file.put("modified", task.outputFile.lastModified());
                    files.put(file);
                }
                result.put("files", files);
                return result.toString();
            } catch (Exception e) {
                return createError(e.getMessage());
            }
        }

        @JavascriptInterface
        public String getAllTasks() {
            JSONArray array = new JSONArray();
            for (DownloadTask task : tasks.values()) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("id", task.id);
                    obj.put("url", task.url);
                    obj.put("path", task.path);
                    obj.put("type", task.type);
                    obj.put("quality", task.quality);
                    obj.put("status", task.status);
                    obj.put("percent", task.percent);
                    obj.put("speed", task.speed);
                    obj.put("eta", task.eta);
                    obj.put("title", task.title);
                    obj.put("filesize", task.filesize);
                    obj.put("created_at", task.createdAt);
                    obj.put("custom_name", task.customName);
                    obj.put("compress", task.compress);
                    obj.put("compression_level", task.compressionLevel);
                    array.put(obj);
                } catch (Exception e) {}
            }
            return array.toString();
        }

        @JavascriptInterface
        public String checkYtDlp() {
            try {
                JSONObject result = new JSONObject();
                if (ytDlpBinary != null && ytDlpBinary.exists()) {
                    Process p = Runtime.getRuntime().exec(new String[]{ytDlpBinary.getAbsolutePath(), "--version"});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String version = reader.readLine();
                    p.waitFor();
                    result.put("installed", true);
                    result.put("version", version);
                    result.put("path", ytDlpBinary.getAbsolutePath());
                } else {
                    result.put("installed", false);
                    result.put("version", "");
                    result.put("path", "");
                }
                return result.toString();
            } catch (Exception e) {
                return createError(e.getMessage());
            }
        }

        @JavascriptInterface
        public String checkFfmpeg() {
            try {
                JSONObject result = new JSONObject();
                if (ffmpegBinary != null && ffmpegBinary.exists()) {
                    Process p = Runtime.getRuntime().exec(new String[]{ffmpegBinary.getAbsolutePath(), "-version"});
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line = reader.readLine();
                    p.waitFor();
                    result.put("installed", true);
                    result.put("version", line != null ? line.split(" ")[2] : "unknown");
                } else {
                    result.put("installed", false);
                }
                return result.toString();
            } catch (Exception e) {
                return createError(e.getMessage());
            }
        }

        @JavascriptInterface
        public String getDiskFree() {
            try {
                File path = Environment.getExternalStorageDirectory();
                long free = path.getFreeSpace();
                long total = path.getTotalSpace();
                JSONObject result = new JSONObject();
                result.put("free", formatSize(free));
                result.put("total", formatSize(total));
                return result.toString();
            } catch (Exception e) {
                return createError(e.getMessage());
            }
        }

        @JavascriptInterface
        public String getAndroidVersion() {
            try {
                JSONObject result = new JSONObject();
                result.put("version", Build.VERSION.RELEASE);
                result.put("sdk", Build.VERSION.SDK_INT);
                result.put("model", Build.MODEL);
                return result.toString();
            } catch (Exception e) {
                return createError(e.getMessage());
            }
        }

        @JavascriptInterface
        public String updateYtDlp() {
            try {
                if (ytDlpBinary == null || !ytDlpBinary.exists()) {
                    return createError("yt-dlp binary not found");
                }
                Process p = Runtime.getRuntime().exec(new String[]{ytDlpBinary.getAbsolutePath(), "-U"});
                p.waitFor();
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("message", "yt-dlp updated");
                return result.toString();
            } catch (Exception e) {
                return createError(e.getMessage());
            }
        }

        @JavascriptInterface
        public void openFile(String path) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    Uri uri = Uri.fromFile(file);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, getMimeType(path));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } else {
                    showToast("الملف غير موجود");
                }
            } catch (Exception e) {
                showToast("لا يمكن فتح الملف");
            }
        }

        @JavascriptInterface
        public void showToast(String message) {
            mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void saveSettings(String settings) {
            getSharedPreferences("YTDownloader", MODE_PRIVATE).edit()
                .putString("settings", settings).apply();
        }

        @JavascriptInterface
        public String loadSettings() {
            return getSharedPreferences("YTDownloader", MODE_PRIVATE)
                .getString("settings", "{}");
        }

        @JavascriptInterface
        public String clearHistory() {
            tasks.clear();
            getSharedPreferences("YTDownloader", MODE_PRIVATE).edit()
                .remove("settings").apply();
            JSONObject result = new JSONObject();
            try { result.put("success", true); } catch (Exception e) {}
            return result.toString();
        }
    }

    private void runYtDlp(DownloadTask task) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(ytDlpBinary.getAbsolutePath());
        cmd.add("--no-warnings");
        cmd.add("--no-check-certificate");
        cmd.add("--retries"); cmd.add("10");
        cmd.add("--fragment-retries"); cmd.add("10");
        cmd.add("--socket-timeout"); cmd.add("60");
        cmd.add("-o");

        String outPath = task.path + "/" + (task.customName.isEmpty() ? "%(title)s.%(ext)s" : task.customName + ".%(ext)s");
        cmd.add(outPath);

        if (task.type.equals("video")) {
            String format;
            switch (task.quality) {
                case "best": format = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"; break;
                case "4k": format = "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/best[height<=2160]"; break;
                case "1440p": format = "bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/best[height<=1440]"; break;
                case "1080p": format = "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]"; break;
                case "720p": format = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]"; break;
                case "480p": format = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]"; break;
                case "360p": format = "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360]"; break;
                default: format = "bestvideo+bestaudio/best";
            }
            cmd.add("-f"); cmd.add(format);
            cmd.add("--merge-output-format"); cmd.add("mp4");
        } else {
            cmd.add("-f"); cmd.add("bestaudio/best");
            cmd.add("-x");
            cmd.add("--audio-format"); cmd.add("mp3");
            cmd.add("--audio-quality"); cmd.add(task.audioQuality);
        }

        cmd.add(task.url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        task.process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(task.process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null && !task.cancelled) {
            parseYtDlpOutput(task, line);
        }

        task.process.waitFor();

        if (!task.cancelled) {
            if (task.compress && task.type.equals("video") && ffmpegBinary != null && ffmpegBinary.exists()) {
                task.status = "compressing";
                compressVideo(task);
            }
            task.status = "completed";
            task.percent = 100;

            // Find output file
            File dir = new File(task.path);
            File[] files = dir.listFiles((d, name) -> name.contains(task.customName.isEmpty() ? "" : task.customName));
            if (files != null && files.length > 0) {
                task.outputFile = files[0];
            }
        }
    }

    private void parseYtDlpOutput(DownloadTask task, String line) {
        // Parse progress from yt-dlp output
        // Example: [download]  25.3% of ~156.78MiB at  2.56MiB/s ETA 00:45
        if (line.contains("[download]") && line.contains("%")) {
            try {
                String percentStr = line.substring(line.indexOf("] ") + 2, line.indexOf("%")).trim();
                task.percent = (int) Float.parseFloat(percentStr);

                if (line.contains("at ")) {
                    String speedPart = line.substring(line.indexOf("at ") + 3);
                    task.speed = speedPart.split(" ")[0] + " " + speedPart.split(" ")[1];
                }
                if (line.contains("ETA ")) {
                    task.eta = line.substring(line.indexOf("ETA ") + 4).trim();
                }
                if (line.contains("of ")) {
                    String sizePart = line.substring(line.indexOf("of ") + 3, line.indexOf(" at"));
                    task.filesize = sizePart.trim();
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        if (line.contains("[ExtractAudio]")) {
            task.status = "processing";
        }
    }

    private void compressVideo(DownloadTask task) throws Exception {
        if (task.outputFile == null || !task.outputFile.exists()) return;

        String crf, preset;
        switch (task.compressionLevel) {
            case "low": crf = "28"; preset = "fast"; break;
            case "high": crf = "36"; preset = "slow"; break;
            default: crf = "32"; preset = "medium";
        }

        File compressed = new File(task.outputFile.getParent(), "compressed_" + task.outputFile.getName());
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegBinary.getAbsolutePath(),
            "-i", task.outputFile.getAbsolutePath(),
            "-c:v", "libx264",
            "-crf", crf,
            "-preset", preset,
            "-c:a", "aac",
            "-b:a", "128k",
            "-y",
            compressed.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();

        if (compressed.exists()) {
            task.outputFile.delete();
            compressed.renameTo(task.outputFile);
        }
    }

    private String createError(String message) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", false);
            result.put("error", message);
            return result.toString();
        } catch (Exception e) {
            return "{"success":false,"error":"" + message + ""}";
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String getMimeType(String path) {
        if (path.endsWith(".mp4")) return "video/mp4";
        if (path.endsWith(".mp3")) return "audio/mpeg";
        if (path.endsWith(".m4a")) return "audio/mp4";
        return "*/*";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
        for (DownloadTask task : tasks.values()) {
            if (task.process != null) task.process.destroyForcibly();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private static class DownloadTask {
        String id, url, path, type, quality, audioQuality, customName, compressionLevel;
        boolean compress, cancelled;
        String status = "pending";
        int percent = 0;
        String speed = "0 B/s", eta = "00:00", title = "", filesize = "0 B", error = "";
        long duration = 0;
        String createdAt;
        Process process;
        File outputFile;

        DownloadTask(String id, String url, String path, String optionsJson) {
            this.id = id;
            this.url = url;
            this.path = path;
            this.createdAt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date());
            try {
                org.json.JSONObject opts = new org.json.JSONObject(optionsJson);
                this.type = opts.optString("type", "video");
                this.quality = opts.optString("quality", "best");
                this.audioQuality = opts.optString("audio_quality", "192");
                this.customName = opts.optString("custom_name", "");
                this.compress = opts.optBoolean("compress", false);
                this.compressionLevel = opts.optString("compression_level", "medium");
            } catch (Exception e) {
                this.type = "video";
                this.quality = "best";
                this.audioQuality = "192";
                this.customName = "";
                this.compress = false;
                this.compressionLevel = "medium";
            }
        }
    }
}