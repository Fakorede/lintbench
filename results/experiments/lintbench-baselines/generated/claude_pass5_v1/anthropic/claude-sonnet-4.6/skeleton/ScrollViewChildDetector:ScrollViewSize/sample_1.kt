package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
            explanation = "ScrollView children must set their `layout_width` or `layout_height` " +
                "attributes to `wrap_content` rather than `fill_parent` or `match_parent` in " +
                "the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"

        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"

        private const val VALUE_FILL_PARENT = "fill_parent"
        private const val VALUE_MATCH_PARENT = "match_parent"

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        SCROLL_VIEW,
        HORIZONTAL_SCROLL_VIEW,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW

        // Iterate over child elements of the ScrollView
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child !is Element) {
                continue
            }

            // For a vertical ScrollView, the scrolling dimension is height.
            // For a HorizontalScrollView, the scrolling dimension is width.
            val scrollingAttr = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

            val attrValue = child.getAttributeNS(ANDROID_NS, scrollingAttr)
                ?: child.getAttribute("android:$scrollingAttr")

            if (attrValue == VALUE_FILL_PARENT || attrValue == VALUE_MATCH_PARENT) {
                val attr = child.getAttributeNodeNS(ANDROID_NS, scrollingAttr)
                    ?: child.getAttributeNode("android:$scrollingAttr")

                val location = if (attr != null) {
                    context.getLocation(attr)
                } else {
                    context.getLocation(child)
                }

                val dimensionName = if (isHorizontal) "layout_width" else "layout_height"
                context.report(
                    ISSUE,
                    child,
                    location,
                    "This child view should set its `$dimensionName` to `wrap_content` " +
                        "rather than `$attrValue` in the scrolling dimension of the parent " +
                        "${element.tagName}.",
                )
            }
        }
    }
}