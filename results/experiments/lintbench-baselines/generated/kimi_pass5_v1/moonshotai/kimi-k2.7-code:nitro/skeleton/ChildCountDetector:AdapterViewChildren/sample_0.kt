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
                `AdapterView` subclasses such as `ListView`, `GridView`, `Spinner`,
                `ExpandableListView`, etc. are populated with data from code using an
                `Adapter`. Declaring child views in XML has no effect and is almost
                always a mistake. Remove the children and set an adapter instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val ADAPTER_VIEW_TAGS = listOf(
            "AdapterView",
            "ListView",
            "GridView",
            "ExpandableListView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewFlipper",
        )
    }

    override fun getApplicableElements(): Collection<String>? = ADAPTER_VIEW_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A `${element.tagName}` cannot have children in XML; use an Adapter instead."
                )
                return
            }
            child = child.nextSibling
        }
    }
}