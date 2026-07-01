package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
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
        private const val FILL_PARENT = "fill_parent"
        private const val MATCH_PARENT = "match_parent"

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
        val isHorizontal = element.localName == HORIZONTAL_SCROLL_VIEW

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                checkChild(context, node, isHorizontal)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element, isHorizontal: Boolean) {
        val androidNs = "http://schemas.android.com/apk/res/android"
        if (isHorizontal) {
            val widthAttr = child.getAttributeNS(androidNs, ATTR_LAYOUT_WIDTH)
            if (widthAttr == FILL_PARENT || widthAttr == MATCH_PARENT) {
                val attrNode = child.getAttributeNodeNS(androidNs, ATTR_LAYOUT_WIDTH)
                context.report(
                    ISSUE,
                    child,
                    if (attrNode != null) context.getLocation(attrNode) else context.getLocation(child),
                    "This child view should use `wrap_content` for its `layout_width` " +
                            "rather than `$widthAttr` in the horizontal scrolling direction"
                )
            }
        } else {
            val heightAttr = child.getAttributeNS(androidNs, ATTR_LAYOUT_HEIGHT)
            if (heightAttr == FILL_PARENT || heightAttr == MATCH_PARENT) {
                val attrNode = child.getAttributeNodeNS(androidNs, ATTR_LAYOUT_HEIGHT)
                context.report(
                    ISSUE,
                    child,
                    if (attrNode != null) context.getLocation(attrNode) else context.getLocation(child),
                    "This child view should use `wrap_content` for its `layout_height` " +
                            "rather than `$heightAttr` in the vertical scrolling direction"
                )
            }
        }
    }
}