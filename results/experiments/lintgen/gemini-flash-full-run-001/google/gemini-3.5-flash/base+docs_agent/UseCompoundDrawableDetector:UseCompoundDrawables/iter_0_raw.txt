package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
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
                """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
            child = child.nextSibling
        }

        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val firstTag = first.tagName
        val secondTag = second.tagName

        val firstIsImg = isImageView(firstTag)
        val firstIsTxt = isTextView(firstTag)
        val secondIsImg = isImageView(secondTag)
        val secondIsTxt = isTextView(secondTag)

        val imageView = if (firstIsImg && secondIsTxt) first else if (secondIsImg && firstIsTxt) second else null
        val textView = if (firstIsImg && secondIsTxt) second else if (secondIsImg && firstIsTxt) first else null

        if (imageView == null || textView == null) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_VISIBILITY)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag can be replaced with a `TextView` using compound drawables"
        )
    }

    private fun isImageView(tag: String): Boolean {
        return tag == IMAGE_VIEW || tag == "androidx.appcompat.widget.AppCompatImageView" || tag.endsWith(".ImageView")
    }

    private fun isTextView(tag: String): Boolean {
        return tag == TEXT_VIEW || tag == "androidx.appcompat.widget.AppCompatTextView" || tag.endsWith(".TextView")
    }
}