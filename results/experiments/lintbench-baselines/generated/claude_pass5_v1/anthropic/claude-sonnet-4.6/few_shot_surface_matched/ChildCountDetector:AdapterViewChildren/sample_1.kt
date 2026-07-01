package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return ADAPTER_VIEW_TYPES
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val firstChild = XmlUtils.getFirstSubTag(element) ?: return
        // There is at least one child element — report on the first child
        context.report(
            ISSUE,
            firstChild,
            context.getLocation(firstChild),
            "A `${element.localName}` should have no children declared in XML: " +
                "`AdapterView` children must be configured from Java code"
        )
    }

    companion object {
        private val ADAPTER_VIEW_TYPES = listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.StackView",
            "android.widget.AdapterView"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation =
                "An `AdapterView` such as a `ListView` must be configured with data from " +
                "Java code, such as a `ListAdapter`. XML children are ignored and should " +
                "not be added.\n\n" +
                "See https://developer.android.com/reference/android/widget/AdapterView.html " +
                "for more information.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}