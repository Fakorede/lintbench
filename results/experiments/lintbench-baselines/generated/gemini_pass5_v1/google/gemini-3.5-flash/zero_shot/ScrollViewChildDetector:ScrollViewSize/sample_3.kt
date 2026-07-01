package com.android.tools.lint.checks

import com.android.SdkConstants
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
import org.w3c.dom.Node

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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.SCROLL_VIEW, SdkConstants.HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == SdkConstants.HORIZONTAL_SCROLL_VIEW
        val targetAttribute = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attrValue = childElement.getAttributeNS(SdkConstants.ANDROID_URI, targetAttribute)
                if (attrValue == VALUE_MATCH_PARENT || attrValue == VALUE_FILL_PARENT) {
                    val msg = if (isHorizontal) {
                        "HorizontalScrollView children should set `android:layout_width` to `wrap_content` rather than `$attrValue`"
                    } else {
                        "ScrollView children should set `android:layout_height` to `wrap_content` rather than `$attrValue`"
                    }
                    val attributeNode = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, targetAttribute)
                    val location = if (attributeNode != null) {
                        context.getLocation(attributeNode)
                    } else {
                        context.getLocation(childElement)
                    }
                    context.report(ISSUE, childElement, location, msg)
                }
            }
        }
    }
}