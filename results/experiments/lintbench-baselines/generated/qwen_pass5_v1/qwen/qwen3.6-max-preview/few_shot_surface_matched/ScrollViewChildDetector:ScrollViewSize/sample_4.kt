package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.TAG_SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SCROLL_VIEW, TAG_HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isVertical = element.tagName == TAG_SCROLL_VIEW
        val attrName = if (isVertical) ATTR_LAYOUT_HEIGHT else ATTR_LAYOUT_WIDTH

        var child = XmlUtils.getFirstSubTag(element)
        while (child != null) {
            val attribute = child.getAttributeNodeNS(ANDROID_URI, attrName)
            if (attribute != null) {
                val value = attribute.value
                if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "This ${if (isVertical) "vertical" else "horizontal"} scrolling view child should set `$attrName` to `wrap_content` instead of `$value`"
                    )
                }
            }
            child = XmlUtils.getNextTag(child)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their `layout_width` or `layout_height` attributes to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ScrollViewChildDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}