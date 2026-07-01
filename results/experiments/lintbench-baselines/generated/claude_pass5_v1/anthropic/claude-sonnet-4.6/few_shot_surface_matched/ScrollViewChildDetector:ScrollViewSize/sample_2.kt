package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
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
import com.android.utils.XmlUtils.getFirstSubTag
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val child = getFirstSubTag(element) ?: return

        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW

        val attributeName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val attribute = child.getAttributeNodeNS(ANDROID_URI, attributeName) ?: return

        val value = attribute.value
        if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
            val dimension = if (isHorizontal) "width" else "height"
            context.report(
                ISSUE,
                child,
                context.getValueLocation(attribute),
                "This child view should set its `layout_$dimension` to `wrap_content` " +
                    "rather than `$value` in the scrolling dimension"
            )
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
                    "in the scrolling dimension.",
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