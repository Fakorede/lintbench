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
            briefDescription = "ScrollView children must use wrap_content in scrolling dimension",
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

    override fun getApplicableElements(): Collection<String> = listOf(
        SdkConstants.SCROLL_VIEW,
        SdkConstants.HORIZONTAL_SCROLL_VIEW
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName.endsWith(SdkConstants.HORIZONTAL_SCROLL_VIEW)
        val attributeName = if (isHorizontal) SdkConstants.ATTR_LAYOUT_WIDTH else SdkConstants.ATTR_LAYOUT_HEIGHT
        val dimension = if (isHorizontal) "horizontal" else "vertical"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attrNode = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, attributeName)
                if (attrNode != null) {
                    val value = attrNode.value
                    if (value == SdkConstants.VALUE_MATCH_PARENT || value == SdkConstants.VALUE_FILL_PARENT) {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(attrNode),
                            "This $dimension scrolling child should use `wrap_content` rather than `$value`"
                        )
                    }
                }
            }
        }
    }
}