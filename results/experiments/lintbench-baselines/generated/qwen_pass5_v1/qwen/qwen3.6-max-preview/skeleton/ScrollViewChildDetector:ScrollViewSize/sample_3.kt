package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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
            explanation = "ScrollView children must set their layout_width or layout_height attributes to wrap_content rather than fill_parent or match_parent in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        "ScrollView",
        "HorizontalScrollView",
        "android.widget.ScrollView",
        "android.widget.HorizontalScrollView"
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName == "HorizontalScrollView" || tagName.endsWith(".HorizontalScrollView")
        val isVertical = tagName == "ScrollView" || tagName.endsWith(".ScrollView")

        if (!isHorizontal && !isVertical) return

        val attrName = if (isHorizontal) "layout_width" else "layout_height"

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                val attr = node.getAttributeNodeNS(ANDROID_NS, attrName)
                if (attr != null) {
                    val value = attr.value
                    if (value == "match_parent" || value == "fill_parent") {
                        context.report(
                            ISSUE,
                            context.getLocation(attr),
                            "Scroll view child should set `$attrName` to `wrap_content` rather than `$value`"
                        )
                    }
                }
            }
        }
    }
}