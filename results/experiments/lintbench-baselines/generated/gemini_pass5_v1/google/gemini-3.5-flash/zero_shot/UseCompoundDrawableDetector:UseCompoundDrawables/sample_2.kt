package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_SCALE_TYPE
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
        }

        if (children.size != 2) {
            return
        }

        val child1 = children[0]
        val child2 = children[1]
        var imageView: Element? = null
        var textView: Element? = null

        val tag1 = child1.tagName
        val tag2 = child2.tagName

        if (tag1 == IMAGE_VIEW) {
            imageView = child1
        } else if (tag1 == TEXT_VIEW) {
            textView = child1
        }

        if (tag2 == IMAGE_VIEW) {
            imageView = child2
        } else if (tag2 == TEXT_VIEW) {
            textView = child2
        }

        if (imageView != null && textView != null) {
            // If the ImageView has an ID, it might be referenced in code, so don't suggest merging
            if (imageView.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
                return
            }
            // If the ImageView has a scaleType, we can't easily merge
            if (imageView.hasAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)) {
                return
            }
            // Check for layout_weight
            if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                return
            }
            // If the LinearLayout has a background and the TextView also has a background, we can't merge
            if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND) &&
                textView.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)
            ) {
                return
            }

            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single `TextView`, using the \
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
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }
}