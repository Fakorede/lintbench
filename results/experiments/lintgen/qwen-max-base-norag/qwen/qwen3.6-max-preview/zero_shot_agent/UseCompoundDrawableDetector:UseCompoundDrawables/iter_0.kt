package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : ResourceXmlDetector() {
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

    override fun getApplicableElements(): Collection<String>? = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
            child = child.nextSibling
        }

        if (children.size != 2) return

        val first = children[0]
        val second = children[1]

        val firstIsImage = isImageView(first)
        val firstIsText = isTextView(first)
        val secondIsImage = isImageView(second)
        val secondIsText = isTextView(second)

        if (!((firstIsImage && secondIsText) || (firstIsText && secondIsImage))) return

        val imageView = if (firstIsImage) first else second
        val textView = if (firstIsText) first else second

        if (hasWeight(imageView) || hasWeight(textView)) return

        val src = imageView.getAttributeNS(ANDROID_URI, ATTR_SRC)
        if (src.isNullOrEmpty()) return

        val location = context.getLocation(element)
        context.report(
            ISSUE,
            element,
            location,
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        )
    }

    private fun isImageView(element: Element): Boolean {
        val tag = element.tagName
        return tag == IMAGE_VIEW || tag.endsWith(".$IMAGE_VIEW")
    }

    private fun isTextView(element: Element): Boolean {
        val tag = element.tagName
        return tag == TEXT_VIEW || tag.endsWith(".$TEXT_VIEW")
    }

    private fun hasWeight(element: Element): Boolean {
        val weight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)
        return weight.isNotEmpty() && weight != "0" && weight != "0.0"
    }
}