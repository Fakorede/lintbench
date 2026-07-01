package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
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
        private val ADAPTER_VIEW_CLASSES = setOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "AbsListView",
            "AbsSpinner",
            "AdapterViewAnimator",
            "AdapterViewFlipper",
            "StackView"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from \
                Java code, such as a `ListAdapter`. Adding children to an `AdapterView` \
                in XML is not valid and will be ignored. If you need a fixed set of views \
                in an `AdapterView`, use a different container widget instead.
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
    }

    override fun getApplicableElements(): Collection<String> {
        return ADAPTER_VIEW_CLASSES
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        var childElementCount = 0
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                childElementCount++
            }
        }

        if (childElementCount > 0) {
            val tagName = element.localName ?: element.tagName
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A `${tagName}` should have no children declared in XML: " +
                        "children are dynamically added using an `Adapter`"
            )
        }
    }
}