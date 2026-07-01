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
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> = listOf(SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        val childCount = (0 until element.childNodes.length).count { index ->
            element.childNodes.item(index).nodeType == Node.ELEMENT_NODE
        }

        if (childCount > 1) {
            context.report(
                issue = ISSUE,
                scope = element,
                location = context.getNameLocation(element),
                message = "ScrollView can have only one child"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView has too many children",
            explanation = """
                A `ScrollView` can only have one child widget. If you want more \
                children, wrap them in a container layout such as `LinearLayout`.
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