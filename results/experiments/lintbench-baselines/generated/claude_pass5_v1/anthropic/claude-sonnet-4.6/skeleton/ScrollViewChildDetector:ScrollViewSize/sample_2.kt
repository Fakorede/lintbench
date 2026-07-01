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
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` \
                attributes to `wrap_content` rather than `fill_parent` or `match_parent` \
                in the scrolling dimension.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"

        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private const val VALUE_FILL_PARENT = "fill_parent"
        private const val VALUE_MATCH_PARENT = "match_parent"
        private const val VALUE_WRAP_CONTENT = "wrap_content"
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        SCROLL_VIEW,
        HORIZONTAL_SCROLL_VIEW,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                checkChild(context, node, isHorizontal)
                // ScrollView should only have one child, but we check all child elements
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element, isHorizontal: Boolean) {
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val value = child.getAttributeNS(ANDROID_NS, attrName)
            .takeIf { it.isNotEmpty() }
            ?: child.getAttribute("android:$attrName")
                .takeIf { it.isNotEmpty() }
            ?: return

        if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
            val attr = child.getAttributeNodeNS(ANDROID_NS, attrName)
                ?: child.getAttributeNode("android:$attrName")

            val location = if (attr != null) {
                context.getLocation(attr)
            } else {
                context.getLocation(child)
            }

            val dimension = if (isHorizontal) "width" else "height"
            context.report(
                ISSUE,
                child,
                location,
                "This child view should set its `layout_$dimension` to `wrap_content` " +
                    "rather than `$value` — the scroll view will stretch to fill its viewport " +
                    "in the scrolling dimension",
            )
        }
    }
}