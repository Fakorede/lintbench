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

    companion object {
        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their `layout_width` or `layout_height` attributes to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.",
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
            "androidx.core.widget.NestedScrollView",
            "android.support.v4.widget.NestedScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isVertical = tagName == "ScrollView" ||
                tagName == "androidx.core.widget.NestedScrollView" ||
                tagName == "android.support.v4.widget.NestedScrollView"
        val isHorizontal = tagName == "HorizontalScrollView"

        if (!isVertical && !isHorizontal) return

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val childNode = childNodes.item(i)
            if (childNode is Element) {
                val attributeName = if (isVertical) "layout_height" else "layout_width"
                val attribute = childNode.getAttributeNodeNS("http://schemas.android.com/apk/res/android", attributeName)
                if (attribute != null) {
                    val value = attribute.value
                    if (value == "match_parent" || value == "fill_parent") {
                        val location = context.getLocation(attribute)
                        context.report(
                            ISSUE,
                            attribute,
                            location,
                            "ScrollView children should set their `$attributeName` to `wrap_content` rather than `$value`"
                        )
                    }
                }
            }
        }
    }
}