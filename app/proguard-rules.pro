# MDNotesWidget ProGuard rules

# Keep all widget-related classes
-keep class com.mdnotes.widget.** { *; }
-keepclassmembers class com.mdnotes.widget.** { *; }

# AppWidgetProvider
-keep class * extends android.appwidget.AppWidgetProvider

# RemoteViewsService for list widget
-keep class * extends android.widget.RemoteViewsService

# Kotlin
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
