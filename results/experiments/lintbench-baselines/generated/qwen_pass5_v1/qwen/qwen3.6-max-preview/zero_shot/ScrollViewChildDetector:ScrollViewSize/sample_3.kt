package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.TAG_SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.resources.ResourceFolderType
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
            briefDescription = "ScrollView size validation",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` attributes \
                to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling \
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_SCROLL_VIEW, TAG_HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == TAG_HORIZONTAL_SCROLL_VIEW
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
        val dimension = if (isHorizontal) "horizontal" else "vertical"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attr = childElement.getAttributeNodeNS(ANDROID_URI, attrName)
                if (attr != null) {
                    val value = attr.value
                    if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                        context.report(
                            ISSUE,
                            context.getLocation(attr),
                            "This $dimension scrolling view child should set `$attrName` to " +
                                "`wrap_content` rather than `$value`"
                        )
                    }
                }
            }
        }
    }
}