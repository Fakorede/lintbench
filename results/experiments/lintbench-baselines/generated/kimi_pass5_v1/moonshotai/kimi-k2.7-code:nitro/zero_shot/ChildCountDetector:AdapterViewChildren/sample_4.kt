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

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = """
                `AdapterView` subclasses such as `ListView`, `GridView`, and `Spinner` \
                must be configured with data from code, for example with a `ListAdapter`. \
                Declaring children inside the `AdapterView` in XML is not supported.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val ADAPTER_VIEW_TAGS = listOf(
            "AdapterView",
            "AbsListView",
            "AbsSpinner",
            "ListView",
            "GridView",
            "ExpandableListView",
            "Spinner",
            "Gallery"
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return ADAPTER_VIEW_TAGS
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`AdapterView` cannot have children in XML"
                )
                return
            }
        }
    }
}