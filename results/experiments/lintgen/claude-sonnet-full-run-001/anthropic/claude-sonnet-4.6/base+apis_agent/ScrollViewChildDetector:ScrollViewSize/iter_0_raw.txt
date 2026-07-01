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
                ScrollView children must set their `layout_width` or `layout_height` attributes \
                to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling \
                dimension.
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
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val attrValue = child.getAttributeNS(
            "http://schemas.android.com/apk/res/android",
            attrName
        )

        if (attrValue == FILL_PARENT || attrValue == MATCH_PARENT) {
            val attr = child.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android",
                attrName
            )
            val scrollDimension = if (isHorizontal) "width" else "height"
            val message = "This child view should set its `layout_$scrollDimension` to " +
                    "`wrap_content` rather than `$attrValue`"
            if (attr != null) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(attr),
                    message
                )
            } else {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    message
                )
            }
        }
    }
}