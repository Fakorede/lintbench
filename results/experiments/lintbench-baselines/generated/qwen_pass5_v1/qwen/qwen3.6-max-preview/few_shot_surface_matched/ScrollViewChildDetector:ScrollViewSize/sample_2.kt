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
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SCROLL_VIEW, TAG_HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == TAG_HORIZONTAL_SCROLL_VIEW
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                val attr = child.getAttributeNodeNS(ANDROID_URI, attrName)
                if (attr != null) {
                    val value = attr.value
                    if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                        context.report(
                            ISSUE,
                            context.getValueLocation(attr),
                            "This ${if (isHorizontal) "HorizontalScrollView" else "ScrollView"} child should set its `$attrName` to `wrap_content` rather than `$value`"
                        )
                    }
                }
            }
            child = child.nextSibling
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