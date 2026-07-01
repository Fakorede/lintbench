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
        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation =
                "An `AdapterView` such as a `ListView` must be configured with data from " +
                "Java code, such as a `ListAdapter`. Adding child views directly in XML " +
                "is not supported and will be ignored.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val ADAPTER_VIEW_CLASSES = listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "AdapterView",
            "AbsListView",
            "AbsSpinner",
            "ExpandableListView",
        )
    }

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_CLASSES

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val childCount = childNodes.length
        for (i in 0 until childCount) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "A list/adapter view should not have any children in XML",
                )
                return
            }
        }
    }
}