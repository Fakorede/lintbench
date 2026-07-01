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
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {

    companion object {
        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"

        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "`ScrollView` can have only one child",
            explanation = "A `ScrollView` (and `HorizontalScrollView`) can only contain one " +
                "direct child widget. If you want to place multiple widgets inside, wrap " +
                "them in a single container layout such as a `LinearLayout`.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.directChildElementCount > 1) {
            context.report(
                issue = ISSUE,
                scope = element,
                location = context.getLocation(element),
                message = "`${element.tagName}` can have only one child",
            )
        }
    }

    private val Element.directChildElementCount: Int
        get() {
            val childNodes = childNodes
            var count = 0
            for (i in 0 until childNodes.length) {
                if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                    count++
                }
            }
            return count
        }
}