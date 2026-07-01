package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : Detector(), XmlScanner {

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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "ScrollView",
            "HorizontalScrollView",
            "NestedScrollView",
            "androidx.core.widget.NestedScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName.endsWith("HorizontalScrollView")
        val attrName = if (isHorizontal) "layout_width" else "layout_height"

        val child = getFirstElementChild(element) ?: return
        val attrNode = child.getAttributeNodeNS(ANDROID_URI, attrName) ?: return
        val value = attrNode.value

        if (value == "match_parent" || value == "fill_parent") {
            context.report(
                ISSUE,
                child,
                context.getLocation(attrNode),
                "This ${element.tagName} child should set `$attrName` to `wrap_content` rather than `$value`"
            )
        }
    }

    private fun getFirstElementChild(parent: Element): Element? {
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                return node as Element
            }
        }
        return null
    }
}