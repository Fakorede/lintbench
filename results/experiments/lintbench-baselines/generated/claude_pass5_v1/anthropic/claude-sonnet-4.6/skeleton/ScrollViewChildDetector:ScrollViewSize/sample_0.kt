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
        var childIndex = 0
        while (childIndex < children.length) {
            val child = children.item(childIndex)
            if (child is Element) {
                checkChild(context, child, isHorizontal)
            }
            childIndex++
        }
    }

    private fun checkChild(context: XmlContext, child: Element, isHorizontal: Boolean) {
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val attrValue = child.getAttributeNS(ANDROID_NS, attrName)
            .takeIf { it.isNotEmpty() }
            ?: child.getAttribute("android:$attrName")

        if (attrValue == VALUE_FILL_PARENT || attrValue == VALUE_MATCH_PARENT) {
            val attrNode = child.getAttributeNodeNS(ANDROID_NS, attrName)
                ?: child.getAttributeNode("android:$attrName")

            val scrollDimension = if (isHorizontal) "horizontal" else "vertical"
            val message = "This dimension will make the $scrollDimension `${child.tagName}` " +
                "match the $scrollDimension dimension of its `${if (isHorizontal) HORIZONTAL_SCROLL_VIEW else SCROLL_VIEW}` parent; " +
                "use `wrap_content` instead"

            if (attrNode != null) {
                context.report(
                    issue = ISSUE,
                    element = child,
                    location = context.getLocation(attrNode),
                    message = message,
                )
            } else {
                context.report(
                    issue = ISSUE,
                    element = child,
                    location = context.getLocation(child),
                    message = message,
                )
            }
        }
    }
}