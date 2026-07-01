package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE_SCROLL_VIEW_SIZE = Issue.create(
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
            "ScrollView",
            "HorizontalScrollView",
            "android.widget.ScrollView",
            "android.widget.HorizontalScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName == "HorizontalScrollView" || tagName == "android.widget.HorizontalScrollView"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, child as Element, isHorizontal)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element, isHorizontal: Boolean) {
        val attrName = if (isHorizontal) "layout_width" else "layout_height"
        val attrNode = child.getAttributeNodeNS(ANDROID_URI, attrName)
        if (attrNode != null) {
            val value = attrNode.value
            if (value == "match_parent" || value == "fill_parent") {
                context.report(
                    ISSUE_SCROLL_VIEW_SIZE,
                    child,
                    context.getLocation(attrNode),
                    "Scrollable children should use `wrap_content` instead of `$value` for `$attrName`"
                )
            }
        }
    }
}