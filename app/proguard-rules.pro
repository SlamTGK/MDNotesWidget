# Add project specific ProGuard rules here.
-keep class com.mdnotes.widget.** { *; }
-keepclassmembers class com.mdnotes.widget.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# AppWidgetProvider
-keep class * extends android.appwidget.AppWidgetProvider

# Kotlin
-keep class kotlin.** { *; }
-dontwarn kotlin.**
