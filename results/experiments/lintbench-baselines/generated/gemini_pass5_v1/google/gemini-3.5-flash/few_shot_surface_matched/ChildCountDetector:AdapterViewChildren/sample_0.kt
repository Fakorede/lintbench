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

class ChildCountDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (!isAdapterView(tagName)) {
            return
        }

        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                context.report(
                    ISSUE,
                    child,
                    context.getNameLocation(child),
                    "An `AdapterView` such as a `ListView` cannot have children declared in XML"
                )
                break
            }
            child = child.nextSibling
        }
    }

    private fun isAdapterView(tagName: String): Boolean {
        val name = tagName.substringAfterLast('.')
        return name == "ListView" ||
                name == "GridView" ||
                name == "Spinner" ||
                name == "Gallery" ||
                name == "StackView" ||
                name == "AdapterViewAnimator" ||
                name == "AdapterViewFlipper" ||
                name == "ExpandableListView" ||
                name == "AdapterView"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data from Java code, such as a `ListAdapter`.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }
}