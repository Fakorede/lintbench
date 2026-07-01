package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "ExpandableListView",
            "StackView",
            "AdapterViewFlipper"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`AdapterView` cannot have children in XML. " +
                    "An `AdapterView` such as a `ListView` must be configured with data from Java code, such as a `ListAdapter`."
                )
                return
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children",
            explanation = "An AdapterView such as a ListView must be configured with data from Java code, such as a ListAdapter.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}