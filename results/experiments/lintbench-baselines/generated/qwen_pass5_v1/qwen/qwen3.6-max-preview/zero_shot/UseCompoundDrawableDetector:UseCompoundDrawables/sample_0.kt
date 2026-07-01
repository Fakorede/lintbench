package com.android.tools.lint.checks

import com.android.SdkConstants
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

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        var imageView: Element? = null
        var textView: Element? = null
        var otherElementCount = 0

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                when (childElement.tagName) {
                    SdkConstants.IMAGE_VIEW -> imageView = childElement
                    SdkConstants.TEXT_VIEW -> textView = childElement
                    else -> otherElementCount++
                }
            }
        }

        if (imageView != null && textView != null && otherElementCount == 0) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This tag and its children can be replaced by one <TextView/> and a compound drawable"
            )
        }
    }

    companion object {
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
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}