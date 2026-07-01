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

        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "`ScrollView` can have only one child",
            explanation = "A `ScrollView` can only have one child widget. If you want more children, wrap them in a container layout.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? =
        listOf(SCROLL_VIEW, "android.widget.ScrollView")

    override fun visitElement(context: XmlContext, element: Element) {
        if (countChildElements(element) > 1) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A `ScrollView` can only have one child widget"
            )
        }
    }

    private fun countChildElements(element: Element): Int {
        var count = 0
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                count++
            }
        }
        return count
    }
}