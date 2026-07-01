package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> = listOf(
        SCROLL_VIEW,
        HORIZONTAL_SCROLL_VIEW
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val child = element.firstChildElement() ?: return

        when (element.tagName) {
            SCROLL_VIEW -> {
                val attr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
                val value = attr?.value
                if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                    context.report(
                        ISSUE,
                        attr ?: child,
                        context.getLocation(attr ?: child),
                        "ScrollView children must set `layout_height` to `wrap_content`, not `match_parent`"
                    )
                }
            }
            HORIZONTAL_SCROLL_VIEW -> {
                val attr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
                val value = attr?.value
                if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                    context.report(
                        ISSUE,
                        attr ?: child,
                        context.getLocation(attr ?: child),
                        "HorizontalScrollView children must set `layout_width` to `wrap_content`, not `match_parent`"
                    )
                }
            }
        }
    }

    private fun Element.firstChildElement(): Element? {
        var node: Node? = firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                return node as Element
            }
            node = node.nextSibling
        }
        return null
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView child uses match_parent in scrolling dimension",
            explanation = """
                A ScrollView (or HorizontalScrollView) can only scroll content that is larger \
                than its own bounds. If the direct child is set to match the ScrollView's size \
                in the scrolling dimension, scrolling is impossible. The child should use \
                wrap_content for layout_height (vertical ScrollView) or layout_width \
                (HorizontalScrollView).
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
}