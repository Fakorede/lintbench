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
        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"
        private const val MATCH_PARENT = "match_parent"
        private const val FILL_PARENT = "fill_parent"
        private const val ATTR_LAYOUT_WIDTH = "android:layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "android:layout_height"

        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "A child of a ScrollView (or HorizontalScrollView) should set its scrolling-dimension layout size to wrap_content rather than match_parent or fill_parent, because the child must be larger than the scrollable container in order to scroll.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? =
        listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.localName ?: element.tagName
        val isHorizontal = tag == HORIZONTAL_SCROLL_VIEW

        val attr = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
        val dimension = if (isHorizontal) "width" else "height"

        for (child in element.directChildren()) {
            val value = child.getAttribute(attr)
            if (value == MATCH_PARENT || value == FILL_PARENT) {
                val location = context.getValueLocation(child, attr)
                val message = "ScrollView children should set android:layout_$dimension to wrap_content, not $value"
                context.report(ISSUE, location, message)
            }
        }
    }

    private fun Element.directChildren(): List<Element> {
        val childNodes = getChildNodes()
        return (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    }
}