package com.android.tools.lint.checks

import com.android.SdkConstants.ADAPTER_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ADAPTER_VIEW_ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from \
                Java code, such as a `ListAdapter`.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            moreInfo = "https://developer.android.com/reference/android/widget/AdapterView.html",
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val ADAPTER_VIEW_SUBCLASSES = setOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "AdapterView",
            "AbsListView",
            "AbsSpinner",
            "ExpandableListView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.AdapterView",
            "android.widget.AbsListView",
            "android.widget.AbsSpinner",
            "android.widget.ExpandableListView"
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return ADAPTER_VIEW_SUBCLASSES
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        var childElementCount = 0
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                childElementCount++
            }
        }

        if (childElementCount > 0) {
            context.report(
                issue = ADAPTER_VIEW_ISSUE,
                location = context.getElementLocation(element),
                message = "A list/grid should have no children declared in XML"
            )
        }
    }
}