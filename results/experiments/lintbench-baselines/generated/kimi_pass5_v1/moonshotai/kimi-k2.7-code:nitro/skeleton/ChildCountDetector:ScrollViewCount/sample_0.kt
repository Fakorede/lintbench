package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"

        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "A `ScrollView` can have only one child",
            explanation = "A `ScrollView` can only contain one direct child widget. If you want to display more than one widget, wrap them in a container layout such as a `LinearLayout`.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        if (countChildElements(element) > 1) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A ScrollView can have only one child",
            )
        }
    }

    private fun countChildElements(element: Element): Int {
        val children = element.childNodes
        var count = 0
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                count++
            }
        }
        return count
    }
}