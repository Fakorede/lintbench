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
        // There is at least one child element; report the issue
        context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "A `${element.localName}` should have no children declared in XML: " +
                "They are created dynamically using an `Adapter`"
        )
    }

    companion object {
        private val ADAPTER_VIEW_TYPES = listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "AdapterView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.AdapterView"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation =
                "An `AdapterView` such as a `ListView` must be configured with data " +
                "from Java code, such as a `ListAdapter`. Adding children to an " +
                "`AdapterView` in XML is not valid and will be ignored.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/reference/android/widget/AdapterView.html"
        )
    }
}