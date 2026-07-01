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
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from \
                Java code, such as a `ListAdapter`. Adding children to an `AdapterView` \
                in XML is not supported — the children will be ignored.
                """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        /** AdapterView subclasses that are commonly used in XML layouts */
        private val ADAPTER_VIEW_CLASSES = listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "AdapterView",
            "AbsListView",
            "AbsSpinner",
        )
    }

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_CLASSES

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val childCount = childNodes.length

        var hasElementChildren = false
        for (i in 0 until childCount) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                hasElementChildren = true
                break
            }
        }

        if (hasElementChildren) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "A `${element.localName}` should have no children declared in XML: " +
                    "the children will be ignored (use an `Adapter` instead)",
            )
        }
    }
}