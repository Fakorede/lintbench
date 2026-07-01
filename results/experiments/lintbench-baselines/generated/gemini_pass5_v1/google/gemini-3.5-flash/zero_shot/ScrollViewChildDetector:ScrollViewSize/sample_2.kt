package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            SdkConstants.SCROLL_VIEW,
            SdkConstants.HORIZONTAL_SCROLL_VIEW,
            "android.widget.ScrollView",
            "android.widget.HorizontalScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName == SdkConstants.HORIZONTAL_SCROLL_VIEW || tagName == "android.widget.HorizontalScrollView"
        val targetAttribute = if (isHorizontal) SdkConstants.ATTR_LAYOUT_WIDTH else SdkConstants.ATTR_LAYOUT_HEIGHT

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val value = child.getAttributeNS(SdkConstants.ANDROID_URI, targetAttribute)
                if (value == SdkConstants.VALUE_MATCH_PARENT || value == SdkConstants.VALUE_FILL_PARENT) {
                    val attributeNode = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, targetAttribute)
                    val location = if (attributeNode != null) {
                        context.getLocation(attributeNode)
                    } else {
                        context.getLocation(child)
                    }
                    context.report(
                        ISSUE,
                        child,
                        location,
                        "ScrollView children must set their `android:$targetAttribute` to `wrap_content` rather than `$value` in the scrolling dimension"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` attributes \
                to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.LAYOUT_SCOPE
            )
        )
    }
}