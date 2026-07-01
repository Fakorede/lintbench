package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.TAG_IMAGE_VIEW
import com.android.SdkConstants.TAG_LINEAR_LAYOUT
import com.android.SdkConstants.TAG_TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : Detector(), XmlScanner {
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

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        var imageView: Element? = null
        var textView: Element? = null
        var childCount = 0

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                childCount++
                val childElement = child as Element
                when (childElement.tagName) {
                    TAG_IMAGE_VIEW -> imageView = childElement
                    TAG_TEXT_VIEW -> textView = childElement
                }
            }
        }

        if (childCount == 2 && imageView != null && textView != null) {
            val imageHasWeight = imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)
            val textHasWeight = textView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)
            if (imageHasWeight || textHasWeight) {
                return
            }

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This tag and its children can be replaced by one <TextView/> and a compound drawable"
            )
        }
    }
}