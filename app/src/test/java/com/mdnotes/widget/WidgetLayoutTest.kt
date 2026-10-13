package com.mdnotes.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetLayoutTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun measure(root: View, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((300 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    @Test fun `compact content wraps and uses remaining space after resize and reapply`() {
        val views = RemoteViews(context.packageName, R.layout.widget_note)
        views.setTextViewText(R.id.widget_title, "Длинный заголовок заметки на две строки")
        views.setTextViewText(R.id.widget_meta, "Заметки")
        views.setTextViewText(R.id.widget_content, "Харизма — это психологическая притягательность. ".repeat(60))
        NoteWidgetProvider.configureContent(views, R.id.widget_content, 12f)
        val root = views.apply(context, FrameLayout(context))
        val content = root.findViewById<TextView>(R.id.widget_content)
        measure(root, 180)
        assertTrue("Text must wrap", content.layout.lineCount > 1)
        assertTrue("Several lines must fit", content.height - content.totalPaddingTop - content.totalPaddingBottom > content.lineHeight * 2)
        assertEquals(root.height, content.bottom)
        val originalHeight = content.height

        // A launcher can reuse the old view hierarchy across application updates.
        content.setSingleLine(true)
        content.maxLines = 1
        views.reapply(context, root)
        measure(root, 240)
        assertTrue("Reapply must restore wrapping", content.layout.lineCount > 1)
        assertTrue("Resizing must enlarge the content area", content.height > originalHeight)
        assertEquals(root.height, content.bottom)
    }
}
