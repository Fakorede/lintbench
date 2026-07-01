package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.CLASS_HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLLVIEW
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
        return listOf(SCROLLVIEW, CLASS_HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val child = getFirstSubTag(element) ?: return

        val isHorizontal = element.tagName == CLASS_HORIZONTAL_SCROLL_VIEW ||
                element.localName == CLASS_HORIZONTAL_SCROLL_VIEW

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
                                "since the parent `HorizontalScrollView` will stretch the child " +
                                "to fill the available horizontal space"
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
                                "since the parent `ScrollView` will stretch the child " +
                                "to fill the available vertical space"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView child has `match_parent` in scrolling dimension",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` \
                attributes to `wrap_content` rather than `fill_parent` or `match_parent` \
                in the scrolling dimension. A child that fills the parent in the scrolling \
                direction defeats the purpose of the scroll view, since there will be \
                nothing to scroll to.
            """,
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