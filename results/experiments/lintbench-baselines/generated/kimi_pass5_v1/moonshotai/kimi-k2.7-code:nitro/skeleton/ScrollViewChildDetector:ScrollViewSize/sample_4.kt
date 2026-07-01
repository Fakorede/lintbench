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
            explanation = "A ScrollView can only scroll its content if the child does not " +
                    "fill the parent in the scrolling direction. For a vertical ScrollView, " +
                    "the direct child should set `android:layout_height` to `wrap_content`. " +
                    "For a horizontal ScrollView, the direct child should set " +
                    "`android:layout_width` to `wrap_content`.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> =
        listOf("ScrollView", "HorizontalScrollView")

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == "HorizontalScrollView"
        val attrName = if (isHorizontal) "layout_width" else "layout_height"
        val dimension = if (isHorizontal) "width" else "height"

        for (child in element.childElements()) {
            val value = child.getAttributeNS(ANDROID_URI, attrName)
            if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                val attr = child.getAttributeNodeNS(ANDROID_URI, attrName) ?: continue
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "The child of a ${element.tagName} should use `wrap_content` for its " +
                            "android:${attrName} (`${dimension}`) instead of `${value}`.",
                )
            }
        }
    }

    private fun Element.childElements(): List<Element> {
        val children = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                children.add(node)
            }
        }
        return children
    }
}