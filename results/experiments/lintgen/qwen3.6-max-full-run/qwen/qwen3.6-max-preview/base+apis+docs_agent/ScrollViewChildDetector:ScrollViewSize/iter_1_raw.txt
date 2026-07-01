package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(ScrollViewChildDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("ScrollView", "HorizontalScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.localName == "HorizontalScrollView"
        val attrName = if (isHorizontal) "layout_width" else "layout_height"
        val children = element.childNodes

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            val attr = childElement.getAttributeNodeNS("http://schemas.android.com/apk/res/android", attrName)
            if (attr != null) {
                val value = attr.value
                if (value == "match_parent" || value == "fill_parent") {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Scroll view child should set $attrName to \"wrap_content\" rather than \"$value\""
                    )
                }
            }
        }
    }
}