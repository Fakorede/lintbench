package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("ScrollView", "HorizontalScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == "HorizontalScrollView"
        val attrName = if (isHorizontal) "layout_width" else "layout_height"
        val dimension = if (isHorizontal) "horizontal" else "vertical"
        val androidUri = "http://schemas.android.com/apk/res/android"

        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val value = childElement.getAttributeNS(androidUri, attrName)
                if (value == "match_parent" || value == "fill_parent") {
                    val attrNode = childElement.getAttributeNodeNS(androidUri, attrName)
                    if (attrNode != null) {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getValueLocation(attrNode),
                            "This $dimension scrolling container child should not use `$attrName=\"$value\"`; use `wrap_content` instead"
                        )
                    }
                }
            }
            child = child.nextSibling
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
            implementation = Implementation(ScrollViewChildDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}