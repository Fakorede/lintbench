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
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var imageView: Element? = null
        var textView: Element? = null
        var childCount = 0

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                childCount++
                val tagName = child.nodeName
                if (tagName == TAG_IMAGE_VIEW) {
                    imageView = child as Element
                } else if (tagName == TAG_TEXT_VIEW) {
                    textView = child as Element
                }
            }
            child = child.nextSibling
        }

        if (childCount == 2 && imageView != null && textView != null) {
            val orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION)
            val isVertical = orientation == VALUE_VERTICAL
            val drawableAttr = if (isVertical) "drawableTop" else "drawableLeft"

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This `LinearLayout` containing an `ImageView` and a `TextView` can be replaced with a single `TextView` using a compound `$drawableAttr`"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Use compound drawables",
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more efficiently handled as a compound drawable (a single TextView, using the `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to draw one or more images adjacent to the text).\n\n" +
                    "If the two widgets are offset from each other with margins, this can be replaced with a `drawablePadding` attribute.\n\n" +
                    "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}