package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.TAG_IMAGE_VIEW
import com.android.SdkConstants.TAG_LINEAR_LAYOUT
import com.android.SdkConstants.TAG_TEXT_VIEW
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
        return listOf(TAG_LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        var imageView: Element? = null
        var textView: Element? = null

        var node: Node? = element.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                childCount++
                val child = node as Element
                val tagName = child.tagName
                if (tagName == TAG_IMAGE_VIEW || tagName.endsWith(":$TAG_IMAGE_VIEW")) {
                    imageView = child
                } else if (tagName == TAG_TEXT_VIEW || tagName.endsWith(":$TAG_TEXT_VIEW")) {
                    textView = child
                }
            }
            node = node.nextSibling
        }

        if (childCount != 2 || imageView == null || textView == null) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT) ||
            textView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Use compound drawables",
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                "efficiently handled as a compound drawable (a single TextView, using the " +
                "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
                "attributes to draw one or more images adjacent to the text).\n\n" +
                "If the two widgets are offset from each other with margins, this can be " +
                "replaced with a `drawablePadding` attribute.\n\n" +
                "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}