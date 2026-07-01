package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        var child = XmlUtils.getFirstSubTag(element)
        while (child != null) {
            childCount++
            child = XmlUtils.getNextTag(child)
        }

        if (childCount > 1) {
            val tag = element.tagName
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A `$tag` can only have one child widget; wrap them in a container layout"
            )
        }
    }

    companion object {
        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can have only one child",
            explanation =
                "A `ScrollView` can only have one child widget. If you want more " +
                "children, wrap them in a container layout.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}