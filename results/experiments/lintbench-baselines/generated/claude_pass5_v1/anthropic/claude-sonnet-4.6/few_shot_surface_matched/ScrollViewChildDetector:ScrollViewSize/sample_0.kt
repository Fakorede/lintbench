package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTag
import com.android.utils.XmlUtils.getNextTag
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW

        var child = getFirstSubTag(element)
        while (child != null) {
            if (isHorizontal) {
                val widthAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
                if (widthAttr != null) {
                    val value = widthAttr.value
                    if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
                        context.report(
                            ISSUE,
                            child,
                            context.getValueLocation(widthAttr),
                            "This child view should use `wrap_content` for its `layout_width` " +
                                "rather than `$value` to allow horizontal scrolling"
                        )
                    }
                }
            } else {
                val heightAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
                if (heightAttr != null) {
                    val value = heightAttr.value
                    if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
                        context.report(
                            ISSUE,
                            child,
                            context.getValueLocation(heightAttr),
                            "This child view should use `wrap_content` for its `layout_height` " +
                                "rather than `$value` to allow vertical scrolling"
                        )
                    }
                }
            }
            child = getNextTag(child)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation =
                "ScrollView children must set their `layout_width` or `layout_height` " +
                "attributes to `wrap_content` rather than `fill_parent` or `match_parent` " +
                "in the scrolling dimension. Using `fill_parent` or `match_parent` in the " +
                "scrolling direction prevents the ScrollView from scrolling, since the child " +
                "will simply be sized to the same dimensions as the ScrollView itself.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}