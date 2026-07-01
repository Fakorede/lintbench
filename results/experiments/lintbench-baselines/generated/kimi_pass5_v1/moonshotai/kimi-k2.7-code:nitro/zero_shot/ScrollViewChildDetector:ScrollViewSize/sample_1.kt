package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ScrollViewChildDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView children size validation",
            explanation = """
                A ScrollView can only scroll its direct child if that child is
                sized to wrap its content in the scrolling dimension. Using
                ``match_parent`` or ``fill_parent`` there defeats scrolling,
                so the child must use ``wrap_content`` for the relevant
                dimension.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            SdkConstants.TAG_SCROLL_VIEW,
            SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val horizontalScroll = element.tagName == SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
        val attr = if (horizontalScroll) {
            SdkConstants.ATTR_LAYOUT_WIDTH
        } else {
            SdkConstants.ATTR_LAYOUT_HEIGHT
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) {
                continue
            }

            val value = child.getAttribute(attr)
            if (value == SdkConstants.VALUE_FILL_PARENT || value == SdkConstants.VALUE_MATCH_PARENT) {
                val message = buildString {
                    append("This ")
                    append(child.tagName)
                    append(" is a child of a ")
                    append(element.tagName)
                    append(" and must use android:")
                    append(attr)
                    append("=\"")
                    append(SdkConstants.VALUE_WRAP_CONTENT)
                    append("\"")
                }

                val fix = LintFix.create()
                    .set(SdkConstants.ANDROID_URI, attr, SdkConstants.VALUE_WRAP_CONTENT)
                    .build()

                context.report(
                    ISSUE,
                    context.getValueLocation(child, attr),
                    message,
                    fix
                )
            }
        }
    }
}