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
import org.w3c.dom.Node

class ScrollViewChildDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView children should set their size to wrap_content in the scrolling dimension",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` attributes \
                to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            SdkConstants.SCROLL_VIEW,
            "android.widget.ScrollView",
            SdkConstants.HORIZONTAL_SCROLL_VIEW,
            "android.widget.HorizontalScrollView",
            "androidx.core.widget.NestedScrollView",
            "android.support.v4.widget.NestedScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName == SdkConstants.HORIZONTAL_SCROLL_VIEW || 
                           tagName == "android.widget.HorizontalScrollView"
        
        val targetAttribute = if (isHorizontal) SdkConstants.ATTR_LAYOUT_WIDTH else SdkConstants.ATTR_LAYOUT_HEIGHT
        
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attrValue = childElement.getAttributeNS(SdkConstants.ANDROID_URI, targetAttribute)
                if (attrValue == SdkConstants.VALUE_MATCH_PARENT || attrValue == SdkConstants.VALUE_FILL_PARENT) {
                    val viewName = if (isHorizontal) "HorizontalScrollView" else "ScrollView"
                    val msg = "$viewName children should set `android:$targetAttribute` to `wrap_content` rather than `$attrValue`"
                    val attributeNode = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, targetAttribute)
                    val location = if (attributeNode != null) context.getLocation(attributeNode) else context.getLocation(childElement)
                    context.report(
                        ISSUE,
                        childElement,
                        location,
                        msg
                    )
                }
            }
            child = child.nextSibling
        }
    }
}