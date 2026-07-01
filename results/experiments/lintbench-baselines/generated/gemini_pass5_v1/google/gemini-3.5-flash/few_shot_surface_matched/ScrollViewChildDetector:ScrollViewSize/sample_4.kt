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

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "ScrollView",
            "HorizontalScrollView",
            "NestedScrollView",
            "androidx.core.widget.NestedScrollView",
            "android.support.v4.widget.NestedScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName.endsWith("HorizontalScrollView")
        val attributeToCheck = if (isHorizontal) "layout_width" else "layout_height"

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val attr = child.getAttributeNodeNS("http://schemas.android.com/apk/res/android", attributeToCheck)
                if (attr != null) {
                    val value = attr.value
                    if (value == "match_parent" || value == "fill_parent") {
                        context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            "ScrollView children must set their `$attributeToCheck` to `wrap_content` rather than `$value` in the scrolling dimension"
                        )
                    }
                }
            }
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
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.LAYOUT_SCOPE
            )
        )
    }
}