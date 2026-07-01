package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> = listOf(SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                childCount++
                if (childCount > 1) {
                    break
                }
            }
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "A ScrollView can only contain one direct child; wrap multiple children in a container layout"
            )
        }
    }

    companion object {
        private const val SCROLL_VIEW = "ScrollView"

        @JvmStatic
        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can only have one child",
            explanation = """
                A ScrollView can only contain one direct child. If you want to place multiple
                widgets inside a ScrollView, wrap them in a single container layout such as
                LinearLayout, ConstraintLayout, or FrameLayout.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}