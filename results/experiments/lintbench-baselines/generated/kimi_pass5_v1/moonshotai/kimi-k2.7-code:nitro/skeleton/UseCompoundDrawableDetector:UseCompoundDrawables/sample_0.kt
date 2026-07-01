package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class UseCompoundDrawableDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val children = element.childElements
        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]
        val firstTag = first.tagName
        val secondTag = second.tagName

        val imageThenText = firstTag == IMAGE_VIEW && secondTag == TEXT_VIEW
        val textThenImage = firstTag == TEXT_VIEW && secondTag == IMAGE_VIEW
        if (!imageThenText && !textThenImage) {
            return
        }

        val orientation = element.getAttributeNS(ANDROID_NS, ATTR_ORIENTATION)
            .takeIf { it.isNotBlank() } ?: VALUE_HORIZONTAL

        val message = when (orientation) {
            VALUE_HORIZONTAL -> if (imageThenText) MSG_LEFT else MSG_RIGHT
            VALUE_VERTICAL -> if (imageThenText) MSG_TOP else MSG_BOTTOM
            else -> return
        }

        context.report(ISSUE, context.getLocation(element), message)
    }

    companion object {
        private const val LINEAR_LAYOUT = "LinearLayout"
        private const val IMAGE_VIEW = "ImageView"
        private const val TEXT_VIEW = "TextView"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val ATTR_ORIENTATION = "orientation"
        private const val VALUE_HORIZONTAL = "horizontal"
        private const val VALUE_VERTICAL = "vertical"

        private const val MSG_LEFT =
            "This tag and its children can be replaced by a single `TextView` using `drawableLeft`"
        private const val MSG_RIGHT =
            "This tag and its children can be replaced by a single `TextView` using `drawableRight`"
        private const val MSG_TOP =
            "This tag and its children can be replaced by a single `TextView` using `drawableTop`"
        private const val MSG_BOTTOM =
            "This tag and its children can be replaced by a single `TextView` using `drawableBottom`"

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
                efficiently handled as a compound drawable (a single `TextView`, using the
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
}

private val org.w3c.dom.Element.childElements: List<org.w3c.dom.Element>
    get() {
        val result = mutableListOf<org.w3c.dom.Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                result.add(node as org.w3c.dom.Element)
            }
        }
        return result
    }