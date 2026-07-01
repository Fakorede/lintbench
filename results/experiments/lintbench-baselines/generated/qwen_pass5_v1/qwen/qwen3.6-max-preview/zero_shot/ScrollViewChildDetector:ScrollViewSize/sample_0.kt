package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
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
    companion object {
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

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName

        val attrName = when (parentTag) {
            SCROLL_VIEW -> ATTR_LAYOUT_HEIGHT
            HORIZONTAL_SCROLL_VIEW -> ATTR_LAYOUT_WIDTH
            else -> return
        }

        val value = element.getAttributeNS(ANDROID_URI, attrName)
        if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
            val attributeNode = element.getAttributeNodeNS(ANDROID_URI, attrName)
            if (attributeNode != null) {
                context.report(
                    ISSUE,
                    context.getLocation(attributeNode),
                    "ScrollView children should use `wrap_content` instead of `$value` in the scrolling dimension"
                )
            }
        }
    }
}