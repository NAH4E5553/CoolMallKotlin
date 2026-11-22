# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# QQ互联 SDK 混淆规则
-keep class com.tencent.** { *; }
-keep class com.tencent.open.** { *; }
-keep class com.tencent.tauth.** { *; }
-keep class com.tencent.connect.** { *; }
-dontwarn com.tencent.**

# FlutterEngine invokes this generated entry point by name.
-keep class io.flutter.plugins.GeneratedPluginRegistrant {
    public static void registerWith(io.flutter.embedding.engine.FlutterEngine);
}

# FlutterBoostActivity and FlutterBoostUtils access these fields by name.
-keepclassmembers class io.flutter.embedding.engine.renderer.FlutterRenderer {
    boolean isDisplayingFlutterUi;
}
-keepclassmembers class io.flutter.plugin.platform.PlatformPlugin {
    *** currentTheme;
}
