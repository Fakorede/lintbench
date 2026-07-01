package com.android.tools.lint.checks

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
        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their layout_width or layout_height attributes to wrap_content rather than fill_parent or match_parent in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
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
        val isHorizontal = element.tagName.endsWith("HorizontalScrollView")
        val attrName = if (isHorizontal) "android:layout_width" else "android:layout_height"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val value = childElement.getAttribute(attrName)
                if (value == "match_parent" || value == "fill_parent") {
                    context.report(
                        ISSUE,
                        context.getAttributeLocation(childElement, attrName),
                        "This child view should set `$attrName` to `wrap_content` instead of `$value` because it is inside a ${element.tagName}"
                    )
                }
            }
        }
    }
}