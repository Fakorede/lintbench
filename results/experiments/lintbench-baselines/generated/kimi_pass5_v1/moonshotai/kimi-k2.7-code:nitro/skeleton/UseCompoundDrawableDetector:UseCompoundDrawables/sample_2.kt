package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : LayoutDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_ID = "id"
        private const val ATTR_LAYOUT_WEIGHT = "layout_weight"
        private const val ATTR_ORIENTATION = "orientation"
        private const val ATTR_SRC = "src"
        private const val HORIZONTAL = "horizontal"
        private const val IMAGE_VIEW = "ImageView"
        private const val LINEAR_LAYOUT = "LinearLayout"
        private const val TEXT_VIEW = "TextView"
        private const val VERTICAL = "vertical"

        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be \
                more efficiently handled as a compound drawable (a single TextView, using \
                the `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.
            """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childElements
        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val imageView: Element
        val textView: Element
        val drawableSide: String

        when {
            first.tagName == IMAGE_VIEW && second.tagName == TEXT_VIEW -> {
                imageView = first
                textView = second
                drawableSide = when (getOrientation(element)) {
                    HORIZONTAL -> "drawableLeft"
                    VERTICAL -> "drawableTop"
                    else -> return
                }
            }
            first.tagName == TEXT_VIEW && second.tagName == IMAGE_VIEW -> {
                textView = first
                imageView = second
                drawableSide = when (getOrientation(element)) {
                    HORIZONTAL -> "drawableRight"
                    VERTICAL -> "drawableBottom"
                    else -> return
                }
            }
            else -> return
        }

        if (!imageView.hasAttributeNS(ANDROID_URI, ATTR_SRC)) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        context.report(
            issue = ISSUE,
            scope = element,
            location = context.getLocation(element),
            message = "This tag and its child elements can be replaced by a single `<TextView>` and a compound drawable (`$drawableSide`)",
        )
    }

    private fun getOrientation(element: Element): String {
        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION)
        return orientation.ifEmpty { HORIZONTAL }
    }

    private val Element.childElements: List<Element>
        get() {
            val result = ArrayList<Element>()
            val nodes = childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    result.add(node as Element)
                }
            }
            return result
        }
}