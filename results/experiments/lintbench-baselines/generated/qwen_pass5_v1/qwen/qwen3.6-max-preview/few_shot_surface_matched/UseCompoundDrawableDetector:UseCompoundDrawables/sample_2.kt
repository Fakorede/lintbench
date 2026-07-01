package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.TAG_IMAGE_VIEW
import com.android.SdkConstants.TAG_LINEAR_LAYOUT
import com.android.SdkConstants.TAG_TEXT_VIEW
import com.android.SdkConstants.VALUE_HORIZONTAL
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_LINEAR_LAYOUT)
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

        if (children.size != 2) return

        val first = children[0]
        val second = children[1]

        val firstIsImage = first.tagName == TAG_IMAGE_VIEW
        val secondIsText = second.tagName == TAG_TEXT_VIEW
        val firstIsText = first.tagName == TAG_TEXT_VIEW
        val secondIsImage = second.tagName == TAG_IMAGE_VIEW

        if (!((firstIsImage && secondIsText) || (firstIsText && secondIsImage))) return

        if (first.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT) ||
            second.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION)
        val isHorizontal = orientation.isEmpty() || orientation == VALUE_HORIZONTAL
        val isVertical = orientation == VALUE_VERTICAL

        if (!isHorizontal && !isVertical) return

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
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                "efficiently handled as a compound drawable (a single TextView, using the " +
                "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
                "attributes to draw one or more images adjacent to the text).\n\n" +
                "If the two widgets are offset from each other with margins, this can be " +
                "replaced with a `drawablePadding` attribute.\n\n" +
                "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}