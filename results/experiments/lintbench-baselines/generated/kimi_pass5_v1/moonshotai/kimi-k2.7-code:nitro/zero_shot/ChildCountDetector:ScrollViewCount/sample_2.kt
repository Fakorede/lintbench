package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf(SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i) is Element) {
                childCount++
            }
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A ScrollView can only have one child widget; found $childCount"
            )
        }
    }

    companion object {
        private const val SCROLL_VIEW = "ScrollView"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can only have one child",
            explanation = """
                A ScrollView can only have one child widget. If you want more children,
                wrap them in a container layout.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}