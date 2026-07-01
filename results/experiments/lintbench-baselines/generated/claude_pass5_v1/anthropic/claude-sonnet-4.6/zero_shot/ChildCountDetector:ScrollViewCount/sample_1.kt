package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_VIEW
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
        private val SCROLL_VIEW_TAGS = setOf(
            "ScrollView",
            "HorizontalScrollView"
        )

        @JvmField
        val SCROLLVIEW_ISSUE = Issue.create(
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
    }

    override fun getApplicableElements(): Collection<String> {
        return SCROLL_VIEW_TAGS
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                childCount++
            }
        }

        if (childCount > 1) {
            context.report(
                issue = SCROLLVIEW_ISSUE,
                location = context.getElementLocation(element),
                message = "`${element.tagName}` can have only one child"
            )
        }
    }
}