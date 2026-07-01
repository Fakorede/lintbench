package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.TAG_IMAGE_VIEW
import com.android.SdkConstants.TAG_LINEAR_LAYOUT
import com.android.SdkConstants.TAG_TEXT_VIEW
import com.android.SdkConstants.VALUE_VERTICAL
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
    override fun getApplicableElements(): Collection<String>? = listOf(TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = getDirectChildElements(element)
        if (children.size != 2) return

        var imageView: Element? = null
        var textView: Element? = null

        for (child in children) {
            when (child.tagName) {
                TAG_IMAGE_VIEW -> imageView = child
                TAG_TEXT_VIEW -> textView = child
                else -> return
            }
        }

        if (imageView == null || textView == null) return

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        )
    }

    private fun getDirectChildElements(element: Element): List<Element> {
        val list = mutableListOf<Element>()
        var node = element.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                list.add(node as Element)
            }
            node = node.nextSibling
        }
        return list
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}