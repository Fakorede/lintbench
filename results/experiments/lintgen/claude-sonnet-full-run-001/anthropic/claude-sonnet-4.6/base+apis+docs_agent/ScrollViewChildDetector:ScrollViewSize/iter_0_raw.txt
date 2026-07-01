package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.FILL_PARENT
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.MATCH_PARENT
import com.android.SdkConstants.SCROLL_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` \
                attributes to `wrap_content` rather than `fill_parent` or `match_parent` \
                in the scrolling dimension.
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = getChildren(element)
        if (children.isEmpty()) {
            return
        }

        val isHorizontal = element.localName == HORIZONTAL_SCROLL_VIEW
        val scrollingDimensionAttr = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        for (child in children) {
            val attrValue = child.getAttributeNS(
                "http://schemas.android.com/apk/res/android",
                scrollingDimensionAttr
            )
            if (attrValue == FILL_PARENT || attrValue == MATCH_PARENT) {
                val attr = child.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android",
                    scrollingDimensionAttr
                )
                val location = if (attr != null) {
                    context.getLocation(attr)
                } else {
                    context.getLocation(child)
                }
                val dimensionName = if (isHorizontal) "layout_width" else "layout_height"
                context.report(
                    ISSUE,
                    child,
                    location,
                    "This child view should set its `$dimensionName` to `wrap_content` " +
                        "rather than `$attrValue` in the scrolling dimension"
                )
            }
        }
    }

    private fun getChildren(element: Element): List<Element> {
        val children = mutableListOf<Element>()
        val nodeList = element.childNodes
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element) {
                children.add(node)
            }
        }
        return children
    }
}