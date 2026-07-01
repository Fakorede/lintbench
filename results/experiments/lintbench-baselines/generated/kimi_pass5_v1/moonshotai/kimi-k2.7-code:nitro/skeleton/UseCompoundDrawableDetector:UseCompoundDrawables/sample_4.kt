package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val LINEAR_LAYOUT = "LinearLayout"
private const val IMAGE_VIEW = "ImageView"
private const val TEXT_VIEW = "TextView"
private val DRAWABLE_ATTRIBUTES = listOf(
    "drawableLeft",
    "drawableRight",
    "drawableTop",
    "drawableBottom",
    "drawableStart",
    "drawableEnd",
)

class UseCompoundDrawableDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
                efficiently handled as a compound drawable (a single TextView, using the
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom`
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be
                replaced with a `drawablePadding` attribute.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        var imageView: Element? = null
        var textView: Element? = null
        var childCount = 0

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                childCount++
                if (childCount > 2) {
                    return
                }
                when (child.tagName) {
                    IMAGE_VIEW -> imageView = child
                    TEXT_VIEW -> textView = child
                    else -> return
                }
            }
        }

        if (childCount != 2 || imageView == null || textView == null) {
            return
        }

        if (hasCompoundDrawable(textView)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by a single `TextView` with compound drawables",
        )
    }

    private fun hasCompoundDrawable(element: Element): Boolean {
        return DRAWABLE_ATTRIBUTES.any { element.getAttributeNS(ANDROID_URI, it).isNotEmpty() }
    }
}