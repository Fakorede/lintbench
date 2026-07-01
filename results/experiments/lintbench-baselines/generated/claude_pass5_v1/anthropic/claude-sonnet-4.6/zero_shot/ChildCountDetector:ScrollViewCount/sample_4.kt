package com.android.tools.lint.checks

import com.android.SdkConstants.SCROLL_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        val SCROLLVIEW_ISSUE: Issue = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can have only one child",
            explanation = """
                A `ScrollView` can only have one child widget. If you want more children, \
                wrap them in a container layout.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childCount = getChildElementCount(element)
        if (childCount > 1) {
            context.report(
                issue = SCROLLVIEW_ISSUE,
                location = context.getNameLocation(element),
                message = "`${element.tagName}` can have only one direct child"
            )
        }
    }

    private fun getChildElementCount(element: Element): Int {
        val childNodes = element.childNodes
        var count = 0
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                count++
            }
        }
        return count
    }
}