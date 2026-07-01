package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.TAG_SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ScrollViewChildDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_SCROLL_VIEW, TAG_HORIZONTAL_SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        val isVertical = element.tagName == TAG_SCROLL_VIEW
        val attrName = if (isVertical) ATTR_LAYOUT_HEIGHT else ATTR_LAYOUT_WIDTH
        val dimension = if (isVertical) "height" else "width"

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attr = childElement.getAttributeNodeNS(ANDROID_URI, attrName) ?: return
                val value = attr.value

                if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                    val fix = LintFix.Builder()
                        .name("Change to wrap_content")
                        .replace()
                        .text(value)
                        .with(VALUE_WRAP_CONTENT)
                        .range(context.getValueLocation(attr))
                        .build()

                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "The child of a ${element.tagName} should use " +
                            "`android:${attrName}`=\"wrap_content\" rather than \"$value\" " +
                            "in the scrolling dimension ($dimension).",
                        fix
                    )
                }
                return
            }
            child = child.nextSibling
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView child uses match_parent in scrolling dimension",
            explanation = """
                The child of a ScrollView must set `layout_height` to `wrap_content` rather
                than `match_parent` or `fill_parent`, because the content must be allowed to
                be taller than the ScrollView itself. Similarly, the child of a
                HorizontalScrollView must set `layout_width` to `wrap_content`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}