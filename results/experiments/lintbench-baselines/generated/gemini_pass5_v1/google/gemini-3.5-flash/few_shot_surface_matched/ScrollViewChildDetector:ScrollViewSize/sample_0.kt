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
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                if (isHorizontal) {
                    val widthAttr = node.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
                    if (widthAttr != null) {
                        val value = widthAttr.value
                        if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                            context.report(
                                ISSUE,
                                widthAttr,
                                context.getLocation(widthAttr),
                                "HorizontalScrollView children should set `layout_width` to `wrap_content` rather than `$value`"
                            )
                        }
                    }
                } else {
                    val heightAttr = node.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
                    if (heightAttr != null) {
                        val value = heightAttr.value
                        if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                            context.report(
                                ISSUE,
                                heightAttr,
                                context.getLocation(heightAttr),
                                "ScrollView children should set `layout_height` to `wrap_content` rather than `$value`"
                            )
                        }
                    }
                }
            }
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
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILE_SCOPE
            )
        )
    }
}