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

# Keep readable crash stack traces (deobfuscate via the generated mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Our enums are persisted by name in DataStore and read back with valueOf()
# (e.g. ScannerMode, order status) — keep their synthetic accessors so R8 can't
# strip/rename the constants out from under the string round-trip.
-keepclassmembers enum me.sourov.quicksale.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ZXing / journeyapps embedded scanner — keep as a safety net (used for the
# camera QR fallback on phones without a hardware scanner).
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }
-dontwarn com.google.zxing.**

# Room, Coil, Paging, DataStore and kotlinx-coroutines ship their own consumer
# rules; WooCommerce JSON is parsed manually via org.json (no reflection), so no
# model keep rules are required. The printer bridge reflects on framework classes
# (android.bld.PrintManager / android.os.SystemProperties), not app classes.