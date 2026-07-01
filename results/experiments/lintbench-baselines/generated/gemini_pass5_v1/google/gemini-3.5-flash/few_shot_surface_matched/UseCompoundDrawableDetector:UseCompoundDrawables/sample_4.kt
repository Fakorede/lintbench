package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childElements = mutableListOf<Element>()
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                childElements.add(child as Element)
            }
            child = child.nextSibling
        }

        if (childElements.size != 2) {
            return
        }

        val first = childElements[0]
        val second = childElements[1]

        val imageView: Element
        val textView: Element

        if (first.tagName == IMAGE_VIEW && second.tagName == TEXT_VIEW) {
            imageView = first
            textView = second
        } else if (first.tagName == TEXT_VIEW && second.tagName == IMAGE_VIEW) {
            imageView = second
            textView = first
        } else {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_VISIBILITY)) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_CLICKABLE) || 
            imageView.hasAttributeNS(ANDROID_URI, "onClick")) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Can be replaced by a `TextView` with compound drawables",
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}