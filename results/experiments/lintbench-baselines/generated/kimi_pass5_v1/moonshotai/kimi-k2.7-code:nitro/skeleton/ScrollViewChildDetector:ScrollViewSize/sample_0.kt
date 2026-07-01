package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"
        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val VALUE_FILL_PARENT = "fill_parent"
        private const val VALUE_MATCH_PARENT = "match_parent"

        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "The direct child of a ScrollView must use wrap_content for its " +
                "height (the scrolling dimension), and the direct child of a " +
                "HorizontalScrollView must use wrap_content for its width. Using match_parent " +
                "or fill_parent in the scrolling dimension means the child is the same size as " +
                "the scroll view, so there is nothing to scroll.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? =
        listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        val child = getFirstChildElement(element) ?: return

        val (attr, dimension) = when (element.tagName) {
            SCROLL_VIEW -> ATTR_LAYOUT_HEIGHT to "height"
            HORIZONTAL_SCROLL_VIEW -> ATTR_LAYOUT_WIDTH to "width"
            else -> return
        }

        val value = child.getAttributeNS(ANDROID_URI, attr)
        if (value.isBlank() || !isFillParent(value)) {
            return
        }

        val message = "The $dimension of a ${element.tagName} child should be set to " +
            "wrap_content, not $value"
        context.report(ISSUE, child, context.getLocation(child), message)
    }

    private fun getFirstChildElement(parent: Element): Element? =
        (0 until parent.childNodes.length)
            .asSequence()
            .mapNotNull { parent.childNodes.item(it) as? Element }
            .firstOrNull()

    private fun isFillParent(value: String): Boolean =
        value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT
}