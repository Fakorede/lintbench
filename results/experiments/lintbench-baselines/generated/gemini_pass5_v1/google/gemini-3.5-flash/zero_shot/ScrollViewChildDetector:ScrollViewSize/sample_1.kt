package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "ScrollView",
            "HorizontalScrollView",
            "android.widget.ScrollView",
            "android.widget.HorizontalScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName == "HorizontalScrollView" || tagName == "android.widget.HorizontalScrollView"
        val targetAttribute = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                val value = node.getAttributeNS(ANDROID_URI, targetAttribute)
                if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                    val attributeNode = node.getAttributeNodeNS(ANDROID_URI, targetAttribute)
                    val location = if (attributeNode != null) {
                        context.getLocation(attributeNode)
                    } else {
                        context.getLocation(node)
                    }
                    context.report(
                        ISSUE,
                        node,
                        location,
                        "ScrollViews should have their child's $targetAttribute set to `wrap_content`"
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
            explanation = "ScrollView children must set their `layout_width` or `layout_height` attributes to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }
}