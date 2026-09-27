# Add project specific ProGuard rules here.

# Keep JavaScript Interface methods and classes for WebView
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class com.example.MainActivity$AndroidBridge {
    public *;
}

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimize Android framework views
-dontwarn android.webkit.**
