package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SdkConstants
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

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

    override fun getApplicableElements(): Collection<String>? =
        listOf(SdkConstants.TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childElements()
        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]
        if (!isImageViewAndTextView(first, second)) {
            return
        }

        val orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION)
        val orientationMessage = when (orientation) {
            SdkConstants.VALUE_VERTICAL -> "drawableTop/drawableBottom"
            else -> "drawableLeft/drawableRight"
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This `LinearLayout` with an `ImageView` and a `TextView` can be replaced by a single `TextView` with compound drawables (e.g. `$orientationMessage`); use `drawablePadding` for any spacing between the text and image",
        )
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = this.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }

    private fun isImageViewAndTextView(first: Element, second: Element): Boolean {
        val firstTag = first.tagName
        val secondTag = second.tagName
        return (firstTag == SdkConstants.TAG_IMAGE_VIEW && secondTag == SdkConstants.TAG_TEXT_VIEW) ||
            (firstTag == SdkConstants.TAG_TEXT_VIEW && secondTag == SdkConstants.TAG_IMAGE_VIEW)
    }
}