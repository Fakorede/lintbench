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
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : LayoutDetector() {

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
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        SCROLL_VIEW,
        HORIZONTAL_SCROLL_VIEW
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
        val dimension = if (isHorizontal) "width" else "height"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attribute = childElement.getAttributeNodeNS(ANDROID_URI, attrName)
                if (attribute != null) {
                    val value = attribute.value
                    if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                        context.report(
                            ISSUE,
                            context.getLocation(attribute),
                            "ScrollView child should set layout_$dimension to \"wrap_content\" rather than \"$value\""
                        )
                    }
                }
            }
        }
    }
}