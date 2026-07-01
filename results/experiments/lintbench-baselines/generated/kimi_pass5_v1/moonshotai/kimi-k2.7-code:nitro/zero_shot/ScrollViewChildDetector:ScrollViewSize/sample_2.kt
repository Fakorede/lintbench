package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.tools.lint.detector.api.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.tools.lint.detector.api.SdkConstants.VALUE_FILL_PARENT
import com.android.tools.lint.detector.api.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> =
        listOf("ScrollView", "HorizontalScrollView")

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == "HorizontalScrollView"
        val attribute = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
        val label = if (isHorizontal) "android:layout_width" else "android:layout_height"

        val child = element.firstElementChild ?: return
        val value = child.getAttributeNS(ANDROID_URI, attribute)
        if (value.isEmpty()) {
            return
        }

        if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
            val attrNode = child.getAttributeNodeNS(ANDROID_URI, attribute) ?: return
            context.report(
                ISSUE,
                child,
                context.getValueLocation(attrNode),
                "The child of a ScrollView must use $VALUE_WRAP_CONTENT for " +
                        "$label instead of $value"
            )
        }
    }

    private val Element.firstElementChild: Element?
        get() {
            var node = firstChild
            while (node != null) {
                if (node.nodeType == Node.ELEMENT_NODE) {
                    return node as Element
                }
                node = node.nextSibling
            }
            return null
        }

    companion object {
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView child size issue",
            explanation = """
                A child of a ScrollView (or HorizontalScrollView) should set its
                dimension in the scrolling direction to wrap_content rather than
                fill_parent or match_parent.
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