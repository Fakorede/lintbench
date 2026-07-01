package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
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
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView child fills parent in scroll direction",
            explanation = """
                Children of a ScrollView or HorizontalScrollView must set their
                dimension in the scrolling direction to `wrap_content`, not
                `match_parent` or `fill_parent`. Using match_parent prevents the
                content from extending beyond the viewport, so scrolling will
                not work correctly.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        "ScrollView",
        "HorizontalScrollView"
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.localName == "HorizontalScrollView"
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val child = element.firstChildElement() ?: return
        val attrValue = child.getAttributeNS(ANDROID_URI, attrName)
        if (attrValue.isEmpty()) return

        if (attrValue == VALUE_MATCH_PARENT || attrValue == VALUE_FILL_PARENT) {
            val attr = child.getAttributeNodeNS(ANDROID_URI, attrName)
            val location = if (attr != null) context.getLocation(attr) else context.getLocation(child)
            context.report(
                ISSUE,
                location,
                "The ${if (isHorizontal) "width" else "height"} of a " +
                        "${element.localName} child must be `wrap_content`, not `$attrValue`"
            )
        }
    }

    private fun Element.firstChildElement(): Element? {
        var node: Node? = this.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                return node as Element
            }
            node = node.nextSibling
        }
        return null
    }
}