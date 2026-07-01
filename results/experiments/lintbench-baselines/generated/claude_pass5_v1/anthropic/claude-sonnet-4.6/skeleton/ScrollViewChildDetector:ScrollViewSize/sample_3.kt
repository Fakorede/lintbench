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

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue

            val child: Element = node

            if (isHorizontal) {
                // For HorizontalScrollView, the scrolling dimension is horizontal (width)
                val layoutWidth = getAttributeValue(child, ATTR_LAYOUT_WIDTH)
                if (layoutWidth == VALUE_FILL_PARENT || layoutWidth == VALUE_MATCH_PARENT) {
                    val attr = child.getAttributeNodeNS(ANDROID_NS, ATTR_LAYOUT_WIDTH)
                        ?: child.getAttributeNode("android:$ATTR_LAYOUT_WIDTH")
                    context.report(
                        ISSUE,
                        child,
                        if (attr != null) context.getLocation(attr) else context.getLocation(child),
                        "This child of a `HorizontalScrollView` sets `layout_width` to `$layoutWidth`; " +
                            "this is not allowed; use `wrap_content` instead",
                    )
                }
            } else {
                // For ScrollView, the scrolling dimension is vertical (height)
                val layoutHeight = getAttributeValue(child, ATTR_LAYOUT_HEIGHT)
                if (layoutHeight == VALUE_FILL_PARENT || layoutHeight == VALUE_MATCH_PARENT) {
                    val attr = child.getAttributeNodeNS(ANDROID_NS, ATTR_LAYOUT_HEIGHT)
                        ?: child.getAttributeNode("android:$ATTR_LAYOUT_HEIGHT")
                    context.report(
                        ISSUE,
                        child,
                        if (attr != null) context.getLocation(attr) else context.getLocation(child),
                        "This child of a `ScrollView` sets `layout_height` to `$layoutHeight`; " +
                            "this is not allowed; use `wrap_content` instead",
                    )
                }
            }
        }
    }

    private fun getAttributeValue(element: Element, localName: String): String? {
        var value = element.getAttributeNS(ANDROID_NS, localName)
        if (value.isNullOrEmpty()) {
            value = element.getAttribute("android:$localName")
        }
        return if (value.isNullOrEmpty()) null else value
    }
}