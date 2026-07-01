package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView child uses match_parent in scrolling dimension",
            explanation = """
                The child of a ScrollView must set its dimension in the scrolling direction \
                to `wrap_content` rather than `match_parent` or `fill_parent`. Using \
                `match_parent` in the scrolling dimension can prevent the ScrollView from \
                measuring and scrolling its content correctly.
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.SCROLL_VIEW, SdkConstants.HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == SdkConstants.HORIZONTAL_SCROLL_VIEW
        val attrName = if (isHorizontal) SdkConstants.ATTR_LAYOUT_WIDTH else SdkConstants.ATTR_LAYOUT_HEIGHT

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, child as Element, attrName, isHorizontal)
            }
            child = child.nextSibling
        }
    }

    private fun checkChild(
        context: XmlContext,
        child: Element,
        attrName: String,
        isHorizontal: Boolean
    ) {
        val attr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, attrName) ?: return
        val value = attr.value
        if (value == SdkConstants.VALUE_MATCH_PARENT || value == SdkConstants.VALUE_FILL_PARENT) {
            val dimension = if (isHorizontal) "layout_width" else "layout_height"
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "ScrollView child should set $dimension to wrap_content, not $value"
            )
        }
    }
}