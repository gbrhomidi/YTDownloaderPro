# ProGuard Rules for YTDownloaderPro
# Keep all Kivy classes
-keep class org.kivy.** { *; }
-keep class org.kivy.android.** { *; }
-keep class org.kivy.core.** { *; }

# Keep Python classes
-keep class com.chaquo.python.** { *; }
-keep class com.google.android.** { *; }

# Keep Flask and related
-keep class flask.** { *; }
-keep class werkzeug.** { *; }
-keep class jinja2.** { *; }
-keep class markupsafe.** { *; }
-keep class click.** { *; }
-keep class itsdangerous.** { *; }

# Keep yt-dlp
-keep class yt_dlp.** { *; }

# Keep WebView
-keep class android.webkit.** { *; }
-keep class androidx.webkit.** { *; }

# Keep FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Keep all PythonActivity
-keep class org.kivy.android.PythonActivity { *; }
-keepclassmembers class org.kivy.android.PythonActivity { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep constructors
-keepclassmembers class * {
    public <init>(...);
}

# Suppress warnings
-dontwarn org.kivy.**
-dontwarn com.chaquo.python.**
-dontwarn flask.**
-dontwarn werkzeug.**
-dontwarn jinja2.**
-dontwarn markupsafe.**
-dontwarn click.**
-dontwarn itsdangerous.**
-dontwarn yt_dlp.**
-dontwarn androidx.webkit.**
-dontwarn android.webkit.**
-dontwarn sun.misc.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
