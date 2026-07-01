package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "ScrollView",
            "HorizontalScrollView",
            "androidx.core.widget.NestedScrollView",
            "android.support.v4.widget.NestedScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == "HorizontalScrollView"
        val targetAttr = if (isHorizontal) "layout_width" else "layout_height"

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val attrNode = child.getAttributeNodeNS("http://schemas.android.com/apk/res/android", targetAttr)
                if (attrNode != null) {
                    val value = attrNode.value
                    if (value == "match_parent" || value == "fill_parent") {
                        val msg = "ScrollView children must set their `$targetAttr` to `wrap_content` rather than `$value` in the scrolling dimension"
                        context.report(
                            ISSUE,
                            attrNode,
                            context.getValueLocation(attrNode),
                            msg
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
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}