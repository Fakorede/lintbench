package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_BACKGROUND = "background"
        private const val ATTR_LAYOUT_WEIGHT = "layout_weight"

        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single `TextView`, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("LinearLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                children.add(node)
            }
        }

        if (children.size != 2) return

        var imageView: Element? = null
        var textView: Element? = null

        for (child in children) {
            val tagName = child.tagName
            if (tagName == "ImageView") {
                imageView = child
            } else if (tagName == "TextView") {
                textView = child
            }
        }

        if (imageView == null || textView == null) return

        // If the ImageView has a background, it's not a simple compound drawable
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) return

        // If the ImageView has a layout weight, it shouldn't be converted straightforwardly
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) return

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
        )
    }
}