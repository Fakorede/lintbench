package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their `layout_width` or `layout_height` attributes to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName

        val isVerticalScroll = parentTag == SdkConstants.SCROLL_VIEW
        val isHorizontalScroll = parentTag == SdkConstants.HORIZONTAL_SCROLL_VIEW

        if (!isVerticalScroll && !isHorizontalScroll) return

        val attrName = if (isVerticalScroll) SdkConstants.ATTR_LAYOUT_HEIGHT else SdkConstants.ATTR_LAYOUT_WIDTH
        val attr = element.getAttributeNode(SdkConstants.ANDROID_PREFIX + attrName) ?: return
        val value = attr.value

        if (value == SdkConstants.VALUE_MATCH_PARENT || value == SdkConstants.VALUE_FILL_PARENT) {
            val dimension = if (isVerticalScroll) "height" else "width"
            val message = "Scroll view child $dimension should be wrap_content, not match_parent or fill_parent"
            context.report(ISSUE, element, context.getLocation(attr), message)
        }
    }
}