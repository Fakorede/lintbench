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
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data \
                from Java code, such as a `ListAdapter`. Adding children in the XML \
                layout will cause a runtime exception.
                """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.LAYOUT_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.ExpandableListView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "A list view cannot have given subviews. Filter matches are added through the Adapter."
                )
            }
        }
    }
}