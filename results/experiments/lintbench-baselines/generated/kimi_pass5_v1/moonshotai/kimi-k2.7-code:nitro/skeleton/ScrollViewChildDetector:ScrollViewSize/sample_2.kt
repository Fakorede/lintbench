package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ScrollViewChildDetector : LayoutDetector() {

    companion object {
        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val VALUE_FILL_PARENT = "fill_parent"
        private const val VALUE_MATCH_PARENT = "match_parent"
        private const val VALUE_WRAP_CONTENT = "wrap_content"

        private const val EXPLANATION =
            "A ScrollView (or HorizontalScrollView) can only scroll its content when the direct " +
            "child is allowed to exceed the viewport in the scrolling direction. The child's " +
            "layout_height (for ScrollView) or layout_width (for HorizontalScrollView) should be " +
            "wrap_content, not fill_parent or match_parent."

        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? =
        listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val dimensionAttr =
            if (element.localName == HORIZONTAL_SCROLL_VIEW) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
        val dimensionName =
            if (dimensionAttr == ATTR_LAYOUT_WIDTH) "width" else "height"

        for (i in 0 until element.childNodes.length) {
            val child = element.childNodes.item(i) as? org.w3c.dom.Element ?: continue
            val attr = child.getAttributeNodeNS(ANDROID_URI, dimensionAttr)
            val value = attr?.value
            if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
                val message =
                    "The $dimensionName of a ScrollView child should be set to \"$VALUE_WRAP_CONTENT\", not \"$value\""
                context.report(
                    ISSUE,
                    attr ?: child,
                    context.getLocation(attr ?: child),
                    message,
                )
            }
        }
    }
}