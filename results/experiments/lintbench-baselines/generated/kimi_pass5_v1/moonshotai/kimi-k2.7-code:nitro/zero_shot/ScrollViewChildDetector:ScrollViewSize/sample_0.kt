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

    override fun getApplicableElements(): Collection<String> = listOf(
        SdkConstants.TAG_SCROLL_VIEW,
        SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
        val attrName = if (isHorizontal) SdkConstants.ATTR_LAYOUT_WIDTH else SdkConstants.ATTR_LAYOUT_HEIGHT

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val value = getLayoutDimension(childElement, attrName) ?: continue

                if (value == SdkConstants.VALUE_FILL_PARENT || value == SdkConstants.VALUE_MATCH_PARENT) {
                    val attribute = "android:$attrName"
                    val message = "The direct child of a ${element.tagName} should set $attribute to `wrap_content` instead of `$value`"
                    context.report(ISSUE, context.getElementLocation(childElement), message)
                }
            }
        }
    }

    private fun getLayoutDimension(element: Element, attrName: String): String? {
        val namespaced = element.getAttributeNS(SdkConstants.ANDROID_URI, attrName)
        if (namespaced.isNotEmpty()) return namespaced

        val prefixed = element.getAttribute("android:$attrName")
        if (prefixed.isNotEmpty()) return prefixed

        return element.getAttribute(attrName).takeUnless { it.isEmpty() }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` attributes
                to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling
                dimension.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}