package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView child uses match_parent/fill_parent in scroll dimension",
            explanation = """
                A child of a ScrollView or HorizontalScrollView should set its dimension \
                along the scroll axis to `wrap_content`. Using `match_parent` or \
                `fill_parent` can prevent the content from scrolling properly.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        SdkConstants.SCROLL_VIEW,
        SdkConstants.HORIZONTAL_SCROLL_VIEW
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val child = element.firstChildElement() ?: return

        val isVertical = element.tagName == SdkConstants.SCROLL_VIEW
        val attrName = if (isVertical) {
            SdkConstants.ATTR_LAYOUT_HEIGHT
        } else {
            SdkConstants.ATTR_LAYOUT_WIDTH
        }

        val value = child.getAttributeNS(SdkConstants.ANDROID_URI, attrName)
        if (value.isEmpty()) {
            return
        }

        if (value == SdkConstants.VALUE_MATCH_PARENT || value == SdkConstants.VALUE_FILL_PARENT) {
            val container = if (isVertical) "ScrollView" else "HorizontalScrollView"
            context.report(
                ISSUE,
                child,
                context.getLocation(child),
                "This `$container` child should set `android:$attrName` to `wrap_content`"
            )
        }
    }

    private fun Element.firstChildElement(): Element? {
        var node = firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                return node as Element
            }
            node = node.nextSibling
        }
        return null
    }
}