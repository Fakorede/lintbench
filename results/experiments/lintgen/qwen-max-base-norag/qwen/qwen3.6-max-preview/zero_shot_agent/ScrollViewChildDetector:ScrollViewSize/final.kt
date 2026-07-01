package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            SdkConstants.SCROLL_VIEW,
            SdkConstants.HORIZONTAL_SCROLL_VIEW
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == SdkConstants.HORIZONTAL_SCROLL_VIEW
        val attrName = if (isHorizontal) SdkConstants.ATTR_LAYOUT_WIDTH else SdkConstants.ATTR_LAYOUT_HEIGHT
        val dimension = if (isHorizontal) "width" else "height"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, attrName)
                if (attr != null) {
                    val value = attr.value
                    if (value == SdkConstants.VALUE_MATCH_PARENT || value == SdkConstants.VALUE_FILL_PARENT) {
                        val message = "Scroll view child $dimension should be wrap_content, not match_parent or fill_parent"
                        context.report(ISSUE, childElement, context.getLocation(attr), message)
                    }
                }
            }
        }
    }
}